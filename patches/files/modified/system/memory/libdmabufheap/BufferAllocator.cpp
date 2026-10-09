/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "DMABUFHEAPS"

#include <BufferAllocator/BufferAllocator.h>

#include <errno.h>
#include <fcntl.h>
#include <linux/dma-buf.h>
#include <linux/dma-heap.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#include <mutex>
#include <shared_mutex>
#include <string>
#include <unordered_set>

#include <android-base/logging.h>
#include <android-base/unique_fd.h>

static constexpr char kDmaHeapRoot[] = "/dev/dma_heap/";

int BufferAllocator::OpenDmabufHeap(const std::string& heap_name) {
    std::shared_lock<std::shared_mutex> slock(dmabuf_heap_fd_mutex_);

    /* Check if heap has already been opened. */
    auto it = dmabuf_heap_fds_.find(heap_name);
    if (it != dmabuf_heap_fds_.end())
        return it->second;

    slock.unlock();

    /*
     * Heap device needs to be opened, use a unique_lock since dmabuf_heap_fd_
     * needs to be modified.
     */
    std::unique_lock<std::shared_mutex> ulock(dmabuf_heap_fd_mutex_);

    /*
     * Check if we already opened this heap again to prevent racing threads from
     * opening the heap device multiple times.
     */
    it = dmabuf_heap_fds_.find(heap_name);
    if (it != dmabuf_heap_fds_.end()) return it->second;

    std::string heap_path = kDmaHeapRoot + heap_name;
    int fd = TEMP_FAILURE_RETRY(open(heap_path.c_str(), O_RDONLY | O_CLOEXEC));
    if (fd < 0) {
        PLOG(ERROR) << "Could not open DMA-BUF heap named: " << heap_name;
        return -errno;
    }

    auto ret = dmabuf_heap_fds_.insert({heap_name, android::base::unique_fd(fd)});
    CHECK(ret.second);
    return fd;
}

// Need to retain the symbol for GRF
BufferAllocator::BufferAllocator() = default;

// User-provided and defaulted in the implementation so the compiler will not inline it
BufferAllocator::~BufferAllocator() = default;

[[deprecated("ION support is removed. Retained for binary compatibility.")]]
int BufferAllocator::MapNameToIonHeap(const std::string&, const std::string&, unsigned int,
                                      unsigned int, unsigned int) {
    /* If ION support is not detected, ignore the mappings */
    return 0;
}

int BufferAllocator::DmabufAlloc(const std::string& heap_name, size_t len, int heap_fd) {
    if (heap_fd < 0) return heap_fd;

    struct dma_heap_allocation_data heap_data{
        .len = len,  // length of data to be allocated in bytes
        .fd_flags = O_RDWR | O_CLOEXEC,  // permissions for the memory to be allocated
    };

    auto ret = TEMP_FAILURE_RETRY(ioctl(heap_fd, DMA_HEAP_IOCTL_ALLOC, &heap_data));
    if (ret < 0) {
        PLOG(ERROR) << "Unable to allocate from DMA-BUF heap: " << heap_name;
        return ret;
    }

    if (heap_data.fd >= 0) {
        if (DmabufSetName(heap_data.fd, heap_name))
            PLOG(WARNING) << "Unable to name DMA buffer for: " << heap_name;
    }

    return heap_data.fd;
}

int BufferAllocator::DmabufSetName(unsigned int dmabuf_fd, const std::string& name) {
    /*
     * Truncate the name here to avoid failure if the length exceeds the limit.
     * length() does not count the '\0' character at the end of the string,
     * but the kernel does, ioctl() would also fail if len == DMA_BUF_NAME_LEN.
     * So we limit the maximum length of the name to 'DMA_BUF_NAME_LEN - 1'.
     */
    const std::string truncated_name = name.substr(0, DMA_BUF_NAME_LEN - 1);
    return TEMP_FAILURE_RETRY(ioctl(dmabuf_fd, DMA_BUF_SET_NAME_B, truncated_name.c_str()));
}

int BufferAllocator::Alloc(const std::string& heap_name, size_t len, unsigned int) {
    int dma_buf_heap_fd = OpenDmabufHeap(heap_name);
    if (dma_buf_heap_fd < 0) return -1;

    return DmabufAlloc(heap_name, len, dma_buf_heap_fd);
}

[[deprecated("ION support is removed. Retained for binary compatibility.")]]
int BufferAllocator::Alloc(const std::string& heap_name, size_t len,
                           unsigned int heap_flags, size_t) {
    return Alloc(heap_name, len, heap_flags);
}

int BufferAllocator::AllocSystem(bool cpu_access_needed, size_t len, unsigned int heap_flags) {
    if (!cpu_access_needed) {
        /*
         * CPU does not need to access allocated buffer so we try to allocate in
         * the 'system-uncached' heap after querying for its existence.
         */
        static bool uncached_dmabuf_system_heap_support = [this]() -> bool {
            auto dmabuf_heap_list = this->GetDmabufHeapList();
            return (dmabuf_heap_list.find(kDmabufSystemUncachedHeapName) != dmabuf_heap_list.end());
        }();

        if (uncached_dmabuf_system_heap_support) {
            int dma_buf_heap_fd = OpenDmabufHeap(kDmabufSystemUncachedHeapName);
            return (dma_buf_heap_fd < 0)
                           ? dma_buf_heap_fd
                           : DmabufAlloc(kDmabufSystemUncachedHeapName, len, dma_buf_heap_fd);
        }
    }

    /*
     * Either 1) CPU needs to access allocated buffer OR 2) CPU does not need to
     * access allocated buffer but the "system-uncached" heap is unsupported.
     */
    return Alloc(kDmabufSystemHeapName, len, heap_flags);
}

[[deprecated("ION support is removed. Retained for binary compatibility.")]]
int BufferAllocator::AllocSystem(bool cpu_access_needed, size_t len, unsigned int heap_flags,
                                 size_t) {
    return AllocSystem(cpu_access_needed, len, heap_flags);
}

int BufferAllocator::DoSync(unsigned int dmabuf_fd, bool start, SyncType sync_type) {
    struct dma_buf_sync sync = {
        .flags = (start ? DMA_BUF_SYNC_START : DMA_BUF_SYNC_END) |
                static_cast<uint64_t>(sync_type),
    };
    return TEMP_FAILURE_RETRY(ioctl(dmabuf_fd, DMA_BUF_IOCTL_SYNC, &sync));
}

int BufferAllocator::CpuSyncStart(unsigned int dmabuf_fd, SyncType sync_type) {
    int ret = DoSync(dmabuf_fd, true, sync_type);
    if (ret) PLOG(ERROR) << "CpuSyncStart() failure";

    return ret;
}

[[deprecated("ION support is removed. Retained for binary compatibility.")]]
int BufferAllocator::CpuSyncStart(unsigned int dmabuf_fd, SyncType sync_type,
                                  const CustomCpuSyncLegacyIon&, void*) {
    return CpuSyncStart(dmabuf_fd, sync_type);
}

int BufferAllocator::CpuSyncEnd(unsigned int dmabuf_fd, SyncType sync_type) {
    int ret = DoSync(dmabuf_fd, false, sync_type);
    if (ret) PLOG(ERROR) << "CpuSyncEnd() failure";

    return ret;
}

[[deprecated("ION support is removed. Retained for binary compatibility.")]]
int BufferAllocator::CpuSyncEnd(unsigned int dmabuf_fd, SyncType sync_type,
                                const CustomCpuSyncLegacyIon&, void*) {
    return CpuSyncEnd(dmabuf_fd, sync_type);
}

std::unordered_set<std::string> BufferAllocator::GetDmabufHeapList() {
    std::unordered_set<std::string> heap_list;
    std::unique_ptr<DIR, int (*)(DIR*)> dir(opendir(kDmaHeapRoot), closedir);

    if (dir) {
        struct dirent* dent;
        while ((dent = readdir(dir.get()))) {
            if (!strcmp(dent->d_name, ".") || !strcmp(dent->d_name, "..")) continue;

            heap_list.insert(dent->d_name);
        }
    }

    return heap_list;
}

[[deprecated("ION support is removed. Retained for binary compatibility.")]]
bool BufferAllocator::CheckIonSupport() {
    return false;
}

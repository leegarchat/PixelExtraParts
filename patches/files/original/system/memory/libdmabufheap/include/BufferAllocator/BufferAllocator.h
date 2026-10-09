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

#pragma once

#include <sys/types.h>

#include <functional>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <android-base/unique_fd.h>

#include <BufferAllocator/dmabufheap-defs.h>


class BufferAllocator {
  public:
    BufferAllocator();
    ~BufferAllocator();

    /* Not copyable or movable */
    BufferAllocator(const BufferAllocator&) = delete;
    BufferAllocator& operator=(const BufferAllocator&) = delete;

    /* *
     * Returns a dmabuf fd if the allocation in one of the specified heaps is successful and
     * an error code otherwise.
     *
     * @heap_name: name of the heap to allocate in.
     * @len: size of the allocation.
     * @heap_flags: flags passed to heap.
     */
    int Alloc(const std::string& heap_name, size_t len, unsigned int heap_flags = 0);

    [[deprecated("ION is not supported. Do not provide ION alignment argument.")]]
    int Alloc(const std::string&, size_t, unsigned int, size_t);

    /* *
     * Returns a dmabuf fd if the allocation in system heap(cached/uncached) is successful and
     * an error code otherwise. Allocates in the 'system' heap if CPU access of
     * the buffer is expected and 'system-uncached' otherwise. If the 'system-uncached'
     * heap is not supported, falls back to the 'system' heap.
     *
     * @cpu_access: indicates if CPU access of the buffer is expected.
     * @len: size of the allocation.
     * @heap_flags: flags passed to heap.
     */
    int AllocSystem(bool cpu_access, size_t len, unsigned int heap_flags = 0);

    /**
     * Must be invoked before CPU access of the allocated memory.
     *
     * @dmabuf_fd: dmabuf file descriptor.
     * @sync_type: specifies if the sync is for read, write or read/write.
     *
     * Returns 0  on success and an error code otherwise.
     */
    int CpuSyncStart(unsigned int dmabuf_fd, SyncType sync_type);

    /**
     * Must be invoked once CPU is done accessing the allocated memory.
     *
     * @dmabuf_fd: dmabuf file descriptor.
     * @sync_type: specifies if sync_type is for read, write or read/write.
     *
     * Returns 0 on success and an error code otherwise.
     */
    int CpuSyncEnd(unsigned int dmabuf_fd, SyncType sync_type);

    /**
     * Query supported DMA-BUF heaps.
     *
     * @return the list of supported DMA-BUF heap names.
     */
    static std::unordered_set<std::string> GetDmabufHeapList();

    /**
     * Set the name of a dma buffer.
     *
     * @dmabuf_fd: dmabuf file descriptor.
     * @name: The name for the dmabuf. Length should not exceed DMA_BUF_NAME_LEN.
     *
     * @return Returns 0 on success, otherwise -1 and sets errno.
     */
    static int DmabufSetName(unsigned int dmabuf_fd, const std::string& name);

    [[deprecated("This function will always fail! ION is not supported.")]]
    int MapNameToIonHeap(const std::string& heap_name, const std::string& ion_heap_name,
                         unsigned int ion_heap_flags = 0, unsigned int legacy_ion_heap_mask = 0,
                         unsigned int legacy_ion_heap_flags = 0);

  private:
    int OpenDmabufHeap(const std::string& name);
    int GetDmabufHeapFd(const std::string& name);
    bool DmabufHeapsSupported() { return !dmabuf_heap_fds_.empty(); }
    int DmabufAlloc(const std::string& heap_name, size_t len, int fd);

    int DoSync(unsigned int dmabuf_fd, bool start, SyncType sync_type);

    /* Stores all open dmabuf_heap handles. */
    std::unordered_map<std::string, android::base::unique_fd> dmabuf_heap_fds_;
    /* Protects dma_buf_heap_fd_ from concurrent access */
    std::shared_mutex dmabuf_heap_fd_mutex_;

    [[deprecated("Retained for ABI compatibility for GRF")]]
    android::base::unique_fd ion_fd_;
    struct [[deprecated("Retained for ABI compatibility for GRF")]] ion_heap_data {
      char name[32];
        __u32 type;
        __u32 heap_id;
        __u32 reserved0;
        __u32 reserved1;
        __u32 reserved2;
    };
    struct [[deprecated("Retained for ABI compatibility for GRF")]] IonHeapConfig {
        unsigned int mask;
        unsigned int flags;
    };
    [[deprecated("Retained for ABI compatibility for GRF")]]
    bool uses_legacy_ion_iface_;
    [[deprecated("Retained for ABI compatibility for GRF")]]
    std::vector<struct ion_heap_data> ion_heap_info_;
    [[deprecated("Retained for ABI compatibility for GRF")]]
    inline static bool logged_interface_;
    [[deprecated("Retained for ABI compatibility for GRF")]]
    std::unordered_map<std::string, struct IonHeapConfig> heap_name_to_config_;
    [[deprecated("Retained for ABI compatibility for GRF")]]
    std::shared_mutex heap_name_to_config_mutex_;


    /*
     * These remain declared so their symbols can be retained for binary compatibility, but no uses
     * are allowed from source code. Moving them to private does not affect their symbol names.
     */
    [[deprecated("This function will always fail! ION is not supported.")]]
    static bool CheckIonSupport();

    [[deprecated("ION is not supported. Do not provide ION arguments.")]]
    int AllocSystem(bool, size_t, unsigned int, size_t);

    [[deprecated("ION is not supported.")]]
    typedef std::function<int(int, int, void *)> CustomCpuSyncLegacyIon;

    [[deprecated("ION is not supported. Do not provide ION arguments.")]]
    int CpuSyncStart(unsigned int, SyncType, const CustomCpuSyncLegacyIon&, void *);

    [[deprecated("ION is not supported. Do not provide ION arguments.")]]
    int CpuSyncEnd(unsigned int, SyncType, const CustomCpuSyncLegacyIon&, void *);
};

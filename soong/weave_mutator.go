// PixelExtraParts compile-time weave (H1), build-time variant.
//
// Vanilla sources stay untouched in upstream trees. During the make phase
// (device.mk) soong/weave/weave_apply.py regenerates patched copies into
// soong/weave_gen/ (git-ignored) from live vanilla + soong/weave/specs/*.patch.
// This mutator then swaps them into the target modules.
//
// Drift behavior: a spec whose context no longer matches live vanilla
// FAILS THE BUILD loudly (no silent frozen forks). Rebase the spec.
package pixelparts

import (
	"android/soong/android"
	"android/soong/java"
)

func init() {
	android.PreArchMutators(registerPixelPartsMutators)
}

func registerPixelPartsMutators(ctx android.RegisterMutatorsContext) {
	ctx.BottomUp("pixelparts_java_weave", pixelPartsJavaWeaveMutator).Parallel()
}

// Stock sources to drop, relative to frameworks/base/.
var frameworkMinusApexExcludes = []string{
	"core/java/android/widget/EdgeEffect.java",
	"core/java/android/widget/Magnifier.java",
	"core/java/android/window/WindowOnBackInvokedDispatcher.java",
	"core/java/android/app/Activity.java",
	"core/java/android/app/ActivityThread.java",
	"core/java/android/app/ApplicationPackageManager.java",
	"core/java/android/content/pm/LauncherActivityInfo.java",
}

// PEP-provided sources, resolved relative to packages/apps/PixelExtraParts/.
var frameworkMinusApexExtraSrcs = []string{
	":pixelparts_weave_framework",
	":pixelparts_framework_helpers",
}

// Stock sources to drop, relative to frameworks/base/packages/SettingsLib/.
var settingsLibExcludes = []string{
	"src/com/android/settingslib/Utils.java",
}

var settingsLibExtraSrcs = []string{
	":pixelparts_weave_settingslib",
}

// Stock sources to drop, relative to frameworks/base/services/core/.
// NOTE: the compiling module is services.core.unboosted
// (java_library_static); services.core itself is a thin static_libs-only
// wrapper and must NOT be touched.
var servicesCoreUnboostedExcludes = []string{
	"java/com/android/server/pm/PackageInstallerService.java",
	"java/com/android/server/pm/PackageInstallerSession.java",
	"java/com/android/server/policy/PhoneWindowManager.java",
	"java/com/android/server/TelephonyRegistry.java",
	"java/com/android/server/notification/NotificationManagerService.java",
}

var servicesCoreUnboostedExtraSrcs = []string{
	":pixelparts_weave_services_core",
	":pixelparts_services_helpers",
}

// Stock sources to drop, relative to frameworks/base/services/.
// DisplayContent lives in services.core-sources-am-wm, compiled ONLY by
// services.impl — hence it is excluded here, not in unboosted.
var servicesImplExcludes = []string{
	"core/java/com/android/server/wm/DisplayContent.java",
	"java/com/android/server/SystemServer.java",
}

var servicesImplExtraSrcs = []string{
	":pixelparts_weave_services_impl",
}

func pixelPartsJavaWeaveMutator(ctx android.BottomUpMutatorContext) {
	var excludes, extraSrcs []string
	switch ctx.ModuleName() {
	case "framework-minus-apex", "framework-minus-apex-headers":
		// NOTE: framework-minus-apex-headers is a separate java_library
		// (same defaults) whose turbine output is what downstream modules
		// actually compile against (header_jar_override). Both need the
		// same weave.
		excludes = frameworkMinusApexExcludes
		extraSrcs = frameworkMinusApexExtraSrcs
	case "SettingsLib":
		excludes = settingsLibExcludes
		extraSrcs = settingsLibExtraSrcs
	case "services.core.unboosted":
		excludes = servicesCoreUnboostedExcludes
		extraSrcs = servicesCoreUnboostedExtraSrcs
	case "services.impl":
		excludes = servicesImplExcludes
		extraSrcs = servicesImplExtraSrcs
	default:
		return
	}

	for _, p := range ctx.Module().GetProperties() {
		if commonProps, ok := p.(*java.CommonProperties); ok {
			if len(excludes) > 0 {
				commonProps.Exclude_srcs.AppendSimpleValue(excludes)
			}
			if len(extraSrcs) > 0 {
				commonProps.Srcs.AppendSimpleValue(extraSrcs)
			}
			return
		}
	}
}

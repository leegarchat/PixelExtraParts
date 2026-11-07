// PixelExtraParts compile-time weave (H1).
//
// Runs in the PreArch phase (before arch variants are split), so each
// target module is visited exactly once. Mutates only the java_library /
// android_library property structs (*java.CommonProperties, registered via
// AddProperties in java/base.go) — no reflection, no upstream file edits.
//
// Targets:
//   framework-minus-apex (frameworks/base, java_library) — full-file
//     replacements via Exclude_srcs + helper sources via Srcs.
//   SettingsLib (frameworks/base/packages/SettingsLib, android_library) —
//     same scheme for SettingsLib/Utils.
//
// Metalava/api-stubs safety: api-stubs-docs reads the filegroup
// :framework-non-updatable-sources directly, while we mutate the
// java_library. The stub source set is therefore untouched.

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
}

// PEP-provided sources, resolved relative to packages/apps/PixelExtraParts/.
var frameworkMinusApexExtraSrcs = []string{
	":pixelparts_framework_weave",
	":pixelparts_framework_helpers",
}

// Stock sources to drop, relative to frameworks/base/packages/SettingsLib/.
var settingsLibExcludes = []string{
	"src/com/android/settingslib/Utils.java",
}

var settingsLibExtraSrcs = []string{
	":pixelparts_settingslib_weave",
}

func pixelPartsJavaWeaveMutator(ctx android.BottomUpMutatorContext) {
	var excludes, extraSrcs []string
	switch ctx.ModuleName() {
	case "framework-minus-apex":
		excludes = frameworkMinusApexExcludes
		extraSrcs = frameworkMinusApexExtraSrcs
	case "SettingsLib":
		excludes = settingsLibExcludes
		extraSrcs = settingsLibExtraSrcs
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

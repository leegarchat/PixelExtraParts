# Copyright (C) 2026 PixelExtraParts contributors
# SPDX-License-Identifier: Apache-2.0
#
# PEP-owned copy of the thermal sysfs symlink setup (mirrors the stock
# pixel-thermal-symlinks flow with pep-unique names so there are zero
# same-name Soong modules / files / services vs stock).
#!/vendor/bin/sh

# Fallback: the stock thermal_symlinks script is functionally identical and
# may have already run (it coexists with this one on trees where its
# overrides: doesn't take). Skip redundant work if the gate prop is set --
# our rc waits on this same prop, so nothing is lost.
if [ "$(getprop vendor.thermal.link_ready)" = "1" ]; then
  exit 0
fi

for f in /sys/class/thermal/thermal_zone*
do
  tz_name=`cat $f/type`
  ln -s $f /dev/thermal/tz-by-name/$tz_name
done
for f in /sys/class/thermal/cooling_device*
do
  cdev_name=`cat $f/type`
  ln -s $f /dev/thermal/cdev-by-name/$cdev_name
done
setprop vendor.thermal.link_ready 1

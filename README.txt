CamLevel v2 - LSPosed module (A36 / GCam as com.samsung.android.ruler)

BUILD: push this folder to a GitHub repo -> Actions -> build -> download CamLevel-apk -> install.
USE:   LSPosed -> Modules -> CamLevel -> enable, scope = com.samsung.android.ruler -> force-stop GCam -> open it.
CHECK: su -c 'logcat -d | grep CamLevel'
MODES (best effort): su -c 'setprop debug.camlevel.mode level'   # FULL level only
                     su -c 'setprop debug.camlevel.mode full'    # level + manual/RAW caps (default)
                     su -c 'setprop debug.camlevel.mode off'     # no change
Hard kill switch: disable the module in LSPosed, force-stop GCam.
If preview goes black: try mode=level first.

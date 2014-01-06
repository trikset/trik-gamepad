namespace trik.gamepad
open Android.Preferences

type SettingsActivity() =
    inherit PreferenceActivity()
    [<Literal>]
    let SK_HOST_ADDRESS = "hostAddress"
    [<Literal>]
    let SK_HOST_PORT    = "hostPort"
    [<Literal>]
    let SK_SHOW_PADS    = "showPads"
    [<Literal>]
    let SK_VIDEO_URI    = "videoURI"

    override this.OnOptionsItemSelected item = // ????
        if item.ItemId = Android.Resource.Id.Button1 then
            Android.Support.V4.App.NavUtils.NavigateUpFromSameTask this 
            true
        else
            base.OnOptionsItemSelected item 
    

    override this.OnPostCreate savedInstanceState =
        base.OnPostCreate savedInstanceState
        this.AddPreferencesFromResource Resource_Xml.pref_general 
        for prefKey in [SK_HOST_ADDRESS; SK_HOST_PORT] do
            let pref = this.FindPreference prefKey
            pref.Summary <- pref.SharedPreferences.GetString(prefKey, "")
            pref.PreferenceChange.Add <| fun e -> e.Preference.Summary <- e.NewValue.ToString()

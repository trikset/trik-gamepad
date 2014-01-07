namespace com.trik.gamepad
open Android.Preferences
open Android.App

[<Activity(Label="@string/title_activity_settings")>]
type SettingsActivity() =
    inherit PreferenceActivity()

    static member val SK_HOST_ADDRESS = "hostAddress"
    static member val SK_HOST_PORT    = "hostPort"
    static member val SK_SHOW_PADS    = "showPads"
    static member val SK_VIDEO_URI    = "videoURI"

    override this.OnOptionsItemSelected item = // ????
        if item.ItemId = Android.Resource.Id.Button1 then
            Android.Support.V4.App.NavUtils.NavigateUpFromSameTask this 
            true
        else
            base.OnOptionsItemSelected item 
    

    override this.OnPostCreate savedInstanceState =
        base.OnPostCreate savedInstanceState
        this.AddPreferencesFromResource Resource_Xml.pref_general 
        for prefKey in [SettingsActivity.SK_HOST_ADDRESS; SettingsActivity.SK_HOST_PORT] do
            let pref = this.FindPreference prefKey
            pref.Summary <- pref.SharedPreferences.GetString(prefKey, "")
            pref.PreferenceChange.Add <| fun e -> e.Preference.Summary <- e.NewValue.ToString()

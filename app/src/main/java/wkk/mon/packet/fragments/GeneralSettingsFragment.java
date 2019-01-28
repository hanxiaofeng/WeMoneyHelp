package wkk.mon.packet.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import wkk.mon.packet.R;

/**
 * Created by Zhongyi on 2/4/16.
 */
public class GeneralSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.general_preferences);
    }
}

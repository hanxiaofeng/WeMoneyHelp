package wkk.mon.packet.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import wkk.mon.packet.R;
import wkk.mon.packet.utils.UpdateTask;


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

package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.dialogOnUiThread;
import static net.kdt.pojavlaunch.Tools.hasMods;
import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.LWJGL3ifyUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private android.widget.TextView mStatusView;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else mInstallJarButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity()));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> {
            if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
                AlertDialog sodiumWarningDialog = new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.sodium_warning_title)
                        .setMessage(R.string.sodium_warning_message)
                        .setNeutralButton(R.string.delete_sodium, (d,w)-> {
                            Tools.deleteSodiumMods();
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        })
                        .create();
                sodiumWarningDialog.show();
            } else ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> {
            if (Tools.isDemoProfile(v.getContext())){ // Say a different message when on demo profile since they might see the hidden demo folder
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);

        });


        // JAVA CRAFT X — módulos
        view.findViewById(R.id.jcx_perf_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.XPerformanceFragment.class, net.kdt.pojavlaunch.jcx.XPerformanceFragment.TAG, null));
        view.findViewById(R.id.jcx_xtouch_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.XTouchFragment.class, net.kdt.pojavlaunch.jcx.XTouchFragment.TAG, null));
        view.findViewById(R.id.jcx_mods_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.ContentLabFragment.class, net.kdt.pojavlaunch.jcx.ContentLabFragment.TAG, net.kdt.pojavlaunch.jcx.ContentLabFragment.args(net.kdt.pojavlaunch.jcx.ContentLabFragment.KIND_MOD)));
        view.findViewById(R.id.jcx_shaders_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.ContentLabFragment.class, net.kdt.pojavlaunch.jcx.ContentLabFragment.TAG, net.kdt.pojavlaunch.jcx.ContentLabFragment.args(net.kdt.pojavlaunch.jcx.ContentLabFragment.KIND_SHADER)));
        view.findViewById(R.id.jcx_packs_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.ContentLabFragment.class, net.kdt.pojavlaunch.jcx.ContentLabFragment.TAG, net.kdt.pojavlaunch.jcx.ContentLabFragment.args(net.kdt.pojavlaunch.jcx.ContentLabFragment.KIND_RESOURCEPACK)));
        view.findViewById(R.id.jcx_worlds_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.WorldManagerFragment.class, net.kdt.pojavlaunch.jcx.WorldManagerFragment.TAG, null));
        view.findViewById(R.id.jcx_versions_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.VersionHubFragment.class, net.kdt.pojavlaunch.jcx.VersionHubFragment.TAG, null));
        view.findViewById(R.id.jcx_engine_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.EngineFragment.class, net.kdt.pojavlaunch.jcx.EngineFragment.TAG, null));
        view.findViewById(R.id.jcx_servers_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.ServersFragment.class, net.kdt.pojavlaunch.jcx.ServersFragment.TAG, null));
        view.findViewById(R.id.jcx_crash_button).setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.jcx.CrashAnalyzerFragment.class, net.kdt.pojavlaunch.jcx.CrashAnalyzerFragment.TAG, null));

        mStatusView = view.findViewById(R.id.jcx_status);
        if (savedInstanceState == null) net.kdt.pojavlaunch.jcx.JcxMotion.entrance((android.view.ViewGroup) view.findViewById(R.id.news_button).getParent());
        net.kdt.pojavlaunch.jcx.JcxMotion.pressFeedback(mPlayButton);

        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        if (mStatusView != null) mStatusView.setText(net.kdt.pojavlaunch.jcx.JcxMotion.statusLine());
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}

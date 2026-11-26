package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment(){
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mMicrosoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button mLocalButton = view.findViewById(R.id.button_local_authentication);

        mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        mLocalButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity(), () -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null)));

        // JAVA CRAFT X — modo convidado: usa o launcher sem conta, sem liberar o jogo sem licença
        Button mGuestButton = view.findViewById(R.id.button_guest_mode);
        mGuestButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.jcx_guest_title)
                .setMessage(R.string.jcx_guest_message)
                .setPositiveButton(R.string.jcx_guest_enter, (d, w) -> {
                    net.kdt.pojavlaunch.jcx.JcxAccounts.setGuest(true);
                    android.widget.Toast.makeText(requireContext(), R.string.jcx_guest_on, android.widget.Toast.LENGTH_SHORT).show();
                    Tools.backToMainMenu(requireActivity());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        // Entrar com OUTRA conta Microsoft: limpa a sessão salva do navegador interno
        view.findViewById(R.id.button_other_account).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG,
                        MicrosoftLoginFragment.freshArgs()));
    }
}

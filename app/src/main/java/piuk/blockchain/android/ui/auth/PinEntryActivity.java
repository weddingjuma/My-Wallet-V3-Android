package piuk.blockchain.android.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import piuk.blockchain.android.R;
import piuk.blockchain.android.data.access.AccessState;
import piuk.blockchain.android.data.api.EnvironmentSettings;
import piuk.blockchain.android.data.currency.CryptoCurrencies;
import piuk.blockchain.android.data.websocket.WebSocketService;
import piuk.blockchain.android.databinding.ActivityPinEntryBinding;
import piuk.blockchain.android.ui.base.BaseAuthActivity;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.ui.swipetoreceive.SwipeToReceiveFragment;
import piuk.blockchain.android.util.AppUtil;
import piuk.blockchain.android.util.OSUtil;
import piuk.blockchain.android.util.PrefsUtil;
import piuk.blockchain.android.util.annotations.Thunk;

public class PinEntryActivity extends BaseAuthActivity implements
        PinEntryFragment.OnPinEntryFragmentInteractionListener,
        ViewPager.OnPageChangeListener {

    private static final int COOL_DOWN_MILLIS = 2 * 1000;
    @Thunk ActivityPinEntryBinding binding;
    private long backPressed;
    // Fragments
    private PinEntryFragment pinEntryFragment;
    private AppUtil appUtil;

    public static void start(Context context) {
        Intent intent = new Intent(context, PinEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        appUtil = new AppUtil(this);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin_entry);
        pinEntryFragment = PinEntryFragment.newInstance(!shouldHideSwipeToReceive());

        final FragmentPagerAdapter fragmentPagerAdapter;
        if (shouldHideSwipeToReceive()) {
            // Don't bother instantiating the QR fragment + Presenter if not necessary
            fragmentPagerAdapter = new SwipeToReceiveFragmentPagerAdapter(
                    getSupportFragmentManager(),
                    pinEntryFragment,
                    new Fragment(),
                    new Fragment());

            lockViewpager();
        } else {
            SwipeToReceiveFragment bitcoinFragment =
                    SwipeToReceiveFragment.newInstance(CryptoCurrencies.BTC);

            SwipeToReceiveFragment ethFragment =
                    SwipeToReceiveFragment.newInstance(CryptoCurrencies.ETHER);

            fragmentPagerAdapter = new SwipeToReceiveFragmentPagerAdapter(
                    getSupportFragmentManager(),
                    pinEntryFragment,
                    bitcoinFragment,
                    ethFragment);

            startWebSocketService();
        }

        binding.viewpager.setOffscreenPageLimit(3);
        binding.viewpager.setAdapter(fragmentPagerAdapter);
        binding.viewpager.addOnPageChangeListener(this);
        binding.indicator.setViewPager(binding.viewpager);

        EnvironmentSettings environmentSettings = new EnvironmentSettings();

        if (environmentSettings.shouldShowDebugMenu()) {
            ToastCustom.makeText(
                    this,
                    "Current environment: "
                            + environmentSettings.getEnvironment().getName(),
                    ToastCustom.LENGTH_SHORT,
                    ToastCustom.TYPE_GENERAL);

            binding.buttonSettings.setVisibility(View.VISIBLE);
            binding.buttonSettings.setOnClickListener(view ->
                    new EnvironmentSwitcher(this, new PrefsUtil(this))
                            .showDebugMenu());
        }
    }

    private boolean shouldHideSwipeToReceive() {
        return getIntent().hasExtra(PinEntryFragment.KEY_VALIDATING_PIN_FOR_RESULT)
                || isCreatingNewPin()
                || !new PrefsUtil(this).getValue(PrefsUtil.KEY_SWIPE_TO_RECEIVE_ENABLED, true);
    }

    private void lockViewpager() {
        binding.viewpager.lockToCurrentPage();
    }

    @Override
    public void onSwipePressed() {
        binding.viewpager.setCurrentItem(1);
    }

    @Override
    public void onBackPressed() {
        if (binding.viewpager.getCurrentItem() != 0) {
            binding.viewpager.setCurrentItem(0);
        } else if (pinEntryFragment != null && pinEntryFragment.isValidatingPinForResult()) {
            finishWithResultCanceled();
        } else if (pinEntryFragment != null && pinEntryFragment.allowExit()) {
            if (backPressed + COOL_DOWN_MILLIS > System.currentTimeMillis()) {
                AccessState.getInstance().logout(this);
                return;
            } else {
                ToastCustom.makeText(this, getString(R.string.exit_confirm), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_GENERAL);
            }

            backPressed = System.currentTimeMillis();
        }
    }

    private void finishWithResultCanceled() {
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    public boolean isCreatingNewPin() {
        return new PrefsUtil(this).getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "").isEmpty();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Test for screen overlays before user enters PIN
        return appUtil.detectObscuredWindow(this, event) || super.dispatchTouchEvent(event);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (position > 0) {
            binding.indicator.setAlpha(1f);
            binding.indicator.setVisibility(View.VISIBLE);
        } else {
            binding.indicator.setAlpha(positionOffset);
        }
    }

    @Override
    public void onPageSelected(int position) {
        pinEntryFragment.resetPinEntry();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // No-op
    }

    private void startWebSocketService() {
        Intent intent = new Intent(this, WebSocketService.class);

        if (!new OSUtil(this).isServiceRunning(WebSocketService.class)) {
            startService(intent);
        } else {
            // Restarting this here ensures re-subscription after app restart - the service may remain
            // running, but the subscription to the WebSocket won't be restarted unless onCreate called
            stopService(intent);
            startService(intent);
        }
    }

    private static class SwipeToReceiveFragmentPagerAdapter extends FragmentPagerAdapter {

        private static final int NUM_ITEMS = 3;
        private final PinEntryFragment pinEntryFragment;
        private final Fragment bitcoinFragment;
        private final Fragment ethFragment;

        SwipeToReceiveFragmentPagerAdapter(FragmentManager fm,
                                           PinEntryFragment pinEntryFragment,
                                           Fragment bitcoinFragment,
                                           Fragment ethFragment) {
            super(fm);
            this.pinEntryFragment = pinEntryFragment;
            this.bitcoinFragment = bitcoinFragment;
            this.ethFragment = ethFragment;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return pinEntryFragment;
                case 1:
                    return bitcoinFragment;
                case 2:
                    return ethFragment;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }
    }

}
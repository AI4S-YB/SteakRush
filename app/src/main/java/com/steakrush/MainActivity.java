package com.steakrush;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class MainActivity extends Activity {
    private AudioManager steakAudioManager;
    private Object gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        steakAudioManager = new AudioManager(this);
        setContentView(createGameContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        steakAudioManager.resume();
        invokeFirstGameLifecycle("onResume", "resume");
    }

    @Override
    protected void onPause() {
        invokeFirstGameLifecycle("onPause", "pause");
        steakAudioManager.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        invokeFirstGameLifecycle("release", "destroy");
        steakAudioManager.release();
        super.onDestroy();
    }

    public AudioManager getSteakAudioManager() {
        return steakAudioManager;
    }

    public void announceCustomerRequest(String doneness) {
        steakAudioManager.speakCustomerRequest(doneness);
    }

    public void announceOrderSuccess() {
        steakAudioManager.speakSuccess();
    }

    public void announceOrderFailure() {
        steakAudioManager.speakFailure();
    }

    private View createGameContent() {
        View reflectedGameView = tryCreateGameView();
        if (reflectedGameView != null) {
            return reflectedGameView;
        }

        FrameLayout root = new FrameLayout(this);
        TextView fallback = new TextView(this);
        fallback.setText(getString(R.string.app_name));
        fallback.setTextSize(24f);
        fallback.setGravity(android.view.Gravity.CENTER);
        root.addView(fallback, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View tryCreateGameView() {
        try {
            Class<?> clazz = Class.forName("com.steakrush.GameView");

            Object instance = tryConstructor(clazz, MainActivity.class, AudioManager.class);
            if (instance == null) {
                instance = tryConstructor(clazz, android.content.Context.class, AudioManager.class);
            }
            if (instance == null) {
                instance = tryConstructor(clazz, MainActivity.class);
            }
            if (instance == null) {
                instance = tryConstructor(clazz, android.content.Context.class);
            }

            if (instance instanceof View) {
                gameView = instance;
                return (View) instance;
            }
        } catch (ClassNotFoundException ignored) {
            // GameView may be created by another worker after this Activity.
        }
        return null;
    }

    private Object tryConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getConstructor(parameterTypes);
            if (parameterTypes.length == 2) {
                return constructor.newInstance(this, steakAudioManager);
            }
            if (parameterTypes.length == 1) {
                return constructor.newInstance(this);
            }
            return constructor.newInstance();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeFirstGameLifecycle(String... methodNames) {
        if (gameView == null) {
            return;
        }
        for (String methodName : methodNames) {
            try {
                Method method = gameView.getClass().getMethod(methodName);
                method.invoke(gameView);
                return;
            } catch (Exception ignored) {
                // Lifecycle hooks are optional for the game view.
            }
        }
    }
}

package com.steakrush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

public class GameView extends View {
    private static final String SHOP_NAME = "\u9648\u53ca\u65f6\u7684\u725b\u6392\u5e97";
    private static final int BG = Color.rgb(36, 42, 39);
    private static final int FLOOR_A = Color.rgb(92, 96, 79);
    private static final int FLOOR_B = Color.rgb(82, 86, 70);
    private static final int COUNTER = Color.rgb(126, 84, 55);
    private static final int COUNTER_EDGE = Color.rgb(83, 52, 38);
    private static final int CREAM = Color.rgb(246, 226, 180);
    private static final int INK = Color.rgb(35, 31, 28);
    private static final int RED = Color.rgb(198, 65, 58);
    private static final int GOLD = Color.rgb(239, 181, 69);
    private static final int GREEN = Color.rgb(79, 178, 109);
    private static final int BLUE = Color.rgb(83, 151, 188);
    private static final int PAN = Color.rgb(37, 40, 45);
    private static final int PAN_EDGE = Color.rgb(16, 19, 23);

    private final GameEngine engine = new GameEngine();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF temp = new RectF();
    private final RectF[] panRects = new RectF[GameEngine.PAN_COUNT];
    private final RectF[] customerRects = new RectF[GameEngine.MAX_CUSTOMERS];
    private final AudioManager audioManager;

    private RectF trashRect = new RectF();
    private RectF helpRect = new RectF();
    private RectF pauseRect = new RectF();
    private RectF resumeRect = new RectF();
    private RectF restartRect = new RectF();
    private RectF tutorialButtonRect = new RectF();
    private long lastFrameTime;
    private int draggingPan = -1;
    private float dragX;
    private float dragY;
    private float downX;
    private float downY;
    private boolean moved;
    private boolean showTutorial = true;
    private boolean gamePaused;
    private String toast = "";
    private String toastDetail = "";
    private float toastTimer;

    public GameView(Context context) {
        this(context, null);
    }

    public GameView(Context context, AudioManager audioManager) {
        super(context);
        this.audioManager = audioManager;
        setFocusable(true);
        setKeepScreenOn(true);
        for (int i = 0; i < panRects.length; i++) {
            panRects[i] = new RectF();
        }
        for (int i = 0; i < customerRects.length; i++) {
            customerRects[i] = new RectF();
        }
        textPaint.setColor(CREAM);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD));
    }

    public void resume() {
        lastFrameTime = SystemClock.uptimeMillis();
        if (audioManager != null) {
            if (gamePaused) {
                audioManager.pause();
            } else {
                audioManager.startMusic();
            }
        }
        invalidate();
    }

    public void pause() {
        lastFrameTime = 0L;
        draggingPan = -1;
        moved = false;
    }

    public void release() {
        pause();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        update();
        layout();
        drawScene(canvas);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (showTutorial) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && tutorialButtonRect.contains(x, y)) {
                showTutorial = false;
                lastFrameTime = SystemClock.uptimeMillis();
            }
            return true;
        }
        if (gamePaused) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handlePauseMenuUp(x, y);
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                draggingPan = -1;
                moved = false;
            }
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                dragX = x;
                dragY = y;
                moved = false;
                draggingPan = findPan(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                dragX = x;
                dragY = y;
                if (Math.abs(x - downX) + Math.abs(y - downY) > dp(18f)) {
                    moved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handleUp(x, y);
                draggingPan = -1;
                moved = false;
                return true;
            default:
                return true;
        }
    }

    private void update() {
        long now = SystemClock.uptimeMillis();
        if (lastFrameTime == 0L) {
            lastFrameTime = now;
        }
        if (showTutorial || gamePaused) {
            lastFrameTime = now;
            if (showTutorial) {
                engine.drainEvents();
            }
            return;
        }
        float dt = Math.min(0.05f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;
        engine.update(dt, cookingPausedPan());
        if (toastTimer > 0f) {
            toastTimer -= dt;
        }
        drainAudioEvents();
    }

    private void drainAudioEvents() {
        if (audioManager == null) {
            engine.drainEvents();
            return;
        }
        List<GameEngine.GameEvent> events = engine.drainEvents();
        for (GameEngine.GameEvent event : events) {
            if (event.type == GameEngine.GameEvent.CUSTOMER_ARRIVED) {
                audioManager.speakCustomerRequest(event.doneness.label);
            } else if (event.type == GameEngine.GameEvent.CUSTOMER_LEFT) {
                audioManager.speak("\u5ba2\u4eba\u7b49\u592a\u4e45\uff0c\u79bb\u5f00\u4e86\u3002");
            }
        }
    }

    private void handleUp(float x, float y) {
        if (pauseRect.contains(x, y)) {
            setGamePaused(true);
            return;
        }

        if (helpRect.contains(x, y)) {
            showTutorial = true;
            lastFrameTime = SystemClock.uptimeMillis();
            return;
        }

        int pan = draggingPan;
        if (pan >= 0 && hasSteak(pan) && moved) {
            int customer = findCustomer(x, y);
            if (customer >= 0) {
                serve(pan, customer);
                return;
            }
            if (trashRect.contains(x, y)) {
                engine.discardSteak(pan);
                showToast("TRASH");
                return;
            }
        }

        int tappedPan = findPan(x, y);
        if (tappedPan >= 0) {
            if (hasSteak(tappedPan)) {
                engine.flipSteak(tappedPan);
                showToast("FLIP");
            } else {
                engine.placeSteak(tappedPan);
                showToast("SIZZLE");
            }
        }
    }

    private void handlePauseMenuUp(float x, float y) {
        if (resumeRect.contains(x, y)) {
            setGamePaused(false);
            showToast("RESUME");
            return;
        }
        if (restartRect.contains(x, y)) {
            restartGame();
        }
    }

    private void setGamePaused(boolean paused) {
        if (gamePaused == paused) {
            return;
        }
        gamePaused = paused;
        draggingPan = -1;
        moved = false;
        lastFrameTime = SystemClock.uptimeMillis();
        if (audioManager != null) {
            if (gamePaused) {
                audioManager.pause();
            } else {
                audioManager.resume();
            }
        }
        invalidate();
    }

    private void restartGame() {
        engine.reset();
        toast = "";
        toastDetail = "";
        toastTimer = 0f;
        setGamePaused(false);
        showToast("RESTART");
    }

    private void serve(int pan, int customer) {
        GameEngine.ServeResult result = engine.serve(pan, customer);
        if (!result.valid) {
            return;
        }
        String sign = result.points >= 0 ? "+" : "";
        showToast(result.message + " " + sign + result.points,
                result.requested.label + "/" + engine.describeCookValue(result.actual)
                        + "  \u5747" + GameEngine.cookDisplayValue(result.actual)
                        + " \u6a59" + GameEngine.cookDisplayValue(result.bottomCook)
                        + " \u84dd" + GameEngine.cookDisplayValue(result.topCook));
        if (audioManager != null) {
            if (result.success) {
                audioManager.speakSuccess();
            } else {
                audioManager.speakFailure();
            }
        }
    }

    private void layout() {
        float w = getWidth();
        float h = getHeight();
        float pad = dp(14f);
        float customerTop = dp(70f);
        float customerHeight = dp(104f);
        float customerGap = dp(7f);
        float customerWidth = (w - pad * 2f - customerGap * 3f) / 4f;
        for (int i = 0; i < customerRects.length; i++) {
            float left = pad + i * (customerWidth + customerGap);
            customerRects[i].set(left, customerTop, left + customerWidth, customerTop + customerHeight);
        }

        float bottomPanelHeight = dp(96f);
        float panAreaTop = customerTop + customerHeight + dp(28f);
        float panAreaBottom = h - bottomPanelHeight - dp(20f);
        float availableH = Math.max(dp(280f), panAreaBottom - panAreaTop);
        float panSize = Math.min((w - pad * 3f) / 2f, (availableH - pad) / 2f);
        panSize = Math.min(panSize, dp(166f));
        float gridW = panSize * 2f + pad;
        float gridH = panSize * 2f + pad;
        float startX = (w - gridW) * 0.5f;
        float startY = panAreaTop + Math.max(0f, (availableH - gridH) * 0.45f);
        for (int i = 0; i < panRects.length; i++) {
            int col = i % 2;
            int row = i / 2;
            float left = startX + col * (panSize + pad);
            float top = startY + row * (panSize + pad);
            panRects[i].set(left, top, left + panSize, top + panSize);
        }

        trashRect.set(w - pad - dp(88f), h - bottomPanelHeight + dp(18f),
                w - pad, h - dp(18f));
    }

    private void drawScene(Canvas canvas) {
        canvas.drawColor(BG);
        drawFloor(canvas);
        drawHeader(canvas);
        drawCustomers(canvas);
        drawCounter(canvas);
        drawPans(canvas);
        drawBottom(canvas);
        drawDraggedSteak(canvas);
        drawToast(canvas);
        drawPauseOverlay(canvas);
        drawTutorial(canvas);
    }

    private void drawFloor(Canvas canvas) {
        float tile = dp(28f);
        for (float y = 0; y < getHeight(); y += tile) {
            for (float x = 0; x < getWidth(); x += tile) {
                paint.setColor((((int) (x / tile) + (int) (y / tile)) & 1) == 0 ? FLOOR_A : FLOOR_B);
                canvas.drawRect(x, y, x + tile + 1f, y + tile + 1f, paint);
            }
        }
    }

    private void drawHeader(Canvas canvas) {
        temp.set(0, 0, getWidth(), dp(58f));
        paint.setColor(Color.rgb(28, 31, 32));
        canvas.drawRect(temp, paint);
        paint.setColor(GOLD);
        canvas.drawRect(0, temp.bottom - dp(4f), getWidth(), temp.bottom, paint);

        textPaint.setColor(CREAM);
        drawFittedCenteredText(canvas, SHOP_NAME, getWidth() * 0.5f, dp(28f),
                sp(22f), sp(15f), Math.max(dp(116f), getWidth() - dp(190f)));

        helpRect.set(getWidth() - dp(48f), dp(8f), getWidth() - dp(12f), dp(42f));
        pauseRect.set(getWidth() - dp(90f), dp(8f), getWidth() - dp(54f), dp(42f));
        drawHeaderButton(canvas, pauseRect, gamePaused ? ">" : "II");
        drawHeaderButton(canvas, helpRect, "?");

        textPaint.setTextSize(sp(14f));
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("SCORE " + engine.getScore(), dp(12f), dp(47f), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("x" + engine.getStreak() + "  MISS " + engine.getMissed(),
                getWidth() - dp(12f), dp(47f), textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawHeaderButton(Canvas canvas, RectF rect, String label) {
        paint.setColor(Color.rgb(63, 68, 70));
        canvas.drawRect(rect, paint);
        paint.setColor(GOLD);
        canvas.drawRect(rect.left, rect.bottom - dp(3f), rect.right, rect.bottom, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(20f));
        textPaint.setColor(CREAM);
        canvas.drawText(label, rect.centerX(), rect.top + dp(25f), textPaint);
    }

    private void drawPauseOverlay(Canvas canvas) {
        if (!gamePaused) {
            return;
        }

        paint.setColor(Color.argb(218, 19, 22, 22));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        float margin = dp(30f);
        float cardWidth = Math.min(getWidth() - margin * 2f, dp(330f));
        float cardHeight = dp(248f);
        float left = (getWidth() - cardWidth) * 0.5f;
        float top = Math.max(dp(102f), (getHeight() - cardHeight) * 0.44f);
        RectF card = temp;
        card.set(left, top, left + cardWidth, top + cardHeight);

        paint.setColor(Color.rgb(244, 220, 166));
        canvas.drawRect(card, paint);
        paint.setColor(Color.rgb(96, 58, 42));
        canvas.drawRect(card.left, card.bottom - dp(8f), card.right, card.bottom, paint);
        paint.setColor(GOLD);
        canvas.drawRect(card.left, card.top, card.right, card.top + dp(7f), paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(INK);
        drawFittedCenteredText(canvas, "PAUSED", card.centerX(), card.top + dp(48f),
                sp(30f), sp(22f), card.width() - dp(34f));
        drawFittedCenteredText(canvas,
                "SCORE " + engine.getScore() + "   TIME " + Math.round(engine.getElapsed()) + "s",
                card.centerX(), card.top + dp(78f), sp(15f), sp(12f), card.width() - dp(34f));

        resumeRect.set(card.left + dp(34f), card.top + dp(104f),
                card.right - dp(34f), card.top + dp(156f));
        restartRect.set(card.left + dp(34f), card.top + dp(170f),
                card.right - dp(34f), card.top + dp(222f));

        drawMenuButton(canvas, resumeRect, GREEN, Color.rgb(45, 105, 65), "RESUME");
        drawMenuButton(canvas, restartRect, RED, Color.rgb(128, 43, 39), "RESTART");
    }

    private void drawMenuButton(Canvas canvas, RectF rect, int fill, int edge, String label) {
        paint.setColor(fill);
        canvas.drawRect(rect, paint);
        paint.setColor(edge);
        canvas.drawRect(rect.left, rect.bottom - dp(5f), rect.right, rect.bottom, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(22f));
        textPaint.setColor(Color.WHITE);
        canvas.drawText(label, rect.centerX(), rect.top + dp(34f), textPaint);
    }

    private void drawCustomers(Canvas canvas) {
        List<GameEngine.Customer> customers = engine.getCustomers();
        for (int i = 0; i < customerRects.length; i++) {
            RectF rect = customerRects[i];
            boolean occupied = i < customers.size();
            paint.setColor(occupied ? Color.rgb(235, 210, 156) : Color.rgb(78, 76, 68));
            canvas.drawRect(rect, paint);
            paint.setColor(occupied ? Color.rgb(74, 48, 38) : Color.rgb(46, 48, 45));
            canvas.drawRect(rect.left, rect.bottom - dp(5f), rect.right, rect.bottom, paint);

            if (occupied) {
                drawCustomer(canvas, rect, customers.get(i));
            } else {
                paint.setColor(Color.rgb(57, 60, 58));
                drawPixelBlock(canvas, rect.centerX() - dp(14f), rect.centerY() - dp(14f),
                        dp(28f), dp(28f), paint.getColor());
            }
        }
    }

    private void drawCustomer(Canvas canvas, RectF rect, GameEngine.Customer customer) {
        float avatar = Math.min(rect.width() * 0.48f, dp(36f));
        float cx = rect.centerX();
        float top = rect.top + dp(8f);
        int shirt = customer.id % 3 == 0 ? BLUE : (customer.id % 3 == 1 ? RED : GREEN);
        drawPixelBlock(canvas, cx - avatar * 0.5f, top + avatar * 0.42f,
                avatar, avatar * 0.62f, shirt);
        drawPixelBlock(canvas, cx - avatar * 0.38f, top,
                avatar * 0.76f, avatar * 0.66f, Color.rgb(232, 177, 128));
        drawPixelBlock(canvas, cx - avatar * 0.42f, top,
                avatar * 0.84f, avatar * 0.20f, Color.rgb(64, 42, 34));
        drawPixelBlock(canvas, cx - avatar * 0.24f, top + avatar * 0.28f,
                avatar * 0.12f, avatar * 0.10f, INK);
        drawPixelBlock(canvas, cx + avatar * 0.12f, top + avatar * 0.28f,
                avatar * 0.12f, avatar * 0.10f, INK);

        textPaint.setTextSize(sp(14f));
        textPaint.setColor(INK);
        canvas.drawText(customer.doneness.label, cx, rect.bottom - dp(26f), textPaint);

        float barLeft = rect.left + dp(8f);
        float barRight = rect.right - dp(8f);
        float barTop = rect.bottom - dp(16f);
        paint.setColor(Color.rgb(92, 75, 57));
        canvas.drawRect(barLeft, barTop, barRight, barTop + dp(7f), paint);
        paint.setColor(customer.patienceFraction() > 0.35f ? GREEN : RED);
        canvas.drawRect(barLeft, barTop,
                barLeft + (barRight - barLeft) * customer.patienceFraction(),
                barTop + dp(7f), paint);
    }

    private void drawCounter(Canvas canvas) {
        float top = getHeight() - dp(104f);
        paint.setColor(COUNTER);
        canvas.drawRect(0, top, getWidth(), getHeight(), paint);
        paint.setColor(COUNTER_EDGE);
        canvas.drawRect(0, top, getWidth(), top + dp(8f), paint);
        float tile = dp(24f);
        for (float x = 0; x < getWidth(); x += tile) {
            paint.setColor(((int) (x / tile) & 1) == 0 ? Color.rgb(143, 93, 59) : COUNTER);
            canvas.drawRect(x, top + dp(10f), x + tile, getHeight(), paint);
        }
    }

    private void drawPans(Canvas canvas) {
        GameEngine.Pan[] pans = engine.getPans();
        for (int i = 0; i < panRects.length; i++) {
            RectF rect = panRects[i];
            paint.setColor(Color.rgb(73, 77, 80));
            canvas.drawRect(rect, paint);
            paint.setColor(Color.rgb(43, 46, 50));
            canvas.drawRect(rect.left + dp(8f), rect.top + dp(8f),
                    rect.right - dp(8f), rect.bottom - dp(8f), paint);

            float panPad = dp(16f);
            temp.set(rect.left + panPad, rect.top + panPad,
                    rect.right - panPad, rect.bottom - panPad);
            paint.setColor(PAN_EDGE);
            canvas.drawOval(temp, paint);
            temp.inset(dp(7f), dp(7f));
            paint.setColor(PAN);
            canvas.drawOval(temp, paint);

            drawHeat(canvas, rect);
            if (pans[i].steak == null) {
                drawEmptyPanMark(canvas, rect);
            } else if (i != draggingPan || !moved) {
                drawSteak(canvas, temp.centerX(), temp.centerY(), temp.width() * 0.58f, pans[i].steak);
                drawSteakHud(canvas, rect, pans[i].steak);
            }
        }
    }

    private void drawHeat(Canvas canvas, RectF rect) {
        float baseY = rect.bottom - dp(19f);
        for (int j = 0; j < 3; j++) {
            float x = rect.centerX() + (j - 1) * dp(18f);
            paint.setColor(j == 1 ? GOLD : RED);
            canvas.drawRect(x - dp(5f), baseY - dp(12f), x + dp(5f), baseY, paint);
        }
    }

    private void drawEmptyPanMark(Canvas canvas, RectF rect) {
        paint.setColor(Color.rgb(88, 94, 98));
        float s = dp(14f);
        drawPixelBlock(canvas, rect.centerX() - s * 1.5f, rect.centerY() - s * 0.5f, s, s, paint.getColor());
        drawPixelBlock(canvas, rect.centerX() - s * 0.5f, rect.centerY() - s * 1.5f, s, s, paint.getColor());
        drawPixelBlock(canvas, rect.centerX() + s * 0.5f, rect.centerY() - s * 0.5f, s, s, paint.getColor());
        drawPixelBlock(canvas, rect.centerX() - s * 0.5f, rect.centerY() + s * 0.5f, s, s, paint.getColor());
    }

    private void drawSteakHud(Canvas canvas, RectF rect, GameEngine.Steak steak) {
        float left = rect.left + dp(12f);
        float top = rect.top + dp(10f);
        float right = rect.right - dp(12f);
        float cook = GameEngine.cookFraction(steak.averageCook());
        paint.setColor(Color.rgb(32, 34, 35));
        canvas.drawRect(left, top, right, top + dp(8f), paint);
        paint.setColor(steak.isBurned() ? RED : (cook > 0.72f ? GOLD : GREEN));
        canvas.drawRect(left, top, left + (right - left) * cook, top + dp(8f), paint);
        drawDonenessTicks(canvas, left, top, right);

        drawSideCookBar(canvas, left, top + dp(12f), right, steak.bottomCook, Color.rgb(242, 116, 64));
        drawSideCookBar(canvas, left, top + dp(18f), right, steak.topCook, Color.rgb(96, 178, 225));

        textPaint.setTextSize(sp(13f));
        textPaint.setColor(isUsefulForWaitingCustomer(steak) ? Color.rgb(205, 255, 188) : CREAM);
        canvas.drawText(engine.describeSteak(steak), rect.centerX(), rect.bottom - dp(10f), textPaint);
    }

    private void drawDonenessTicks(Canvas canvas, float left, float top, float right) {
        paint.setColor(Color.argb(210, 255, 245, 198));
        for (GameEngine.Doneness doneness : GameEngine.Doneness.values()) {
            float x = left + (right - left) * GameEngine.cookFraction(doneness.target);
            canvas.drawRect(x - dp(1f), top - dp(2f), x + dp(1f), top + dp(10f), paint);
        }
    }

    private void drawSideCookBar(Canvas canvas, float left, float top, float right, float value, int color) {
        float width = right - left;
        paint.setColor(Color.rgb(31, 33, 35));
        canvas.drawRect(left, top, right, top + dp(4f), paint);
        paint.setColor(color);
        canvas.drawRect(left, top, left + width * GameEngine.cookFraction(value),
                top + dp(4f), paint);
    }

    private boolean isUsefulForWaitingCustomer(GameEngine.Steak steak) {
        float cook = steak.averageCook();
        for (GameEngine.Customer customer : engine.getCustomers()) {
            if (Math.abs(cook - customer.doneness.target) <= customer.doneness.tolerance) {
                return !steak.isBurned();
            }
        }
        return false;
    }

    private void drawBottom(Canvas canvas) {
        float top = getHeight() - dp(104f);
        RectF crate = new RectF(dp(16f), top + dp(18f), dp(116f), getHeight() - dp(18f));
        paint.setColor(Color.rgb(91, 55, 42));
        canvas.drawRect(crate, paint);
        paint.setColor(Color.rgb(166, 72, 65));
        drawPixelBlock(canvas, crate.centerX() - dp(26f), crate.centerY() - dp(18f), dp(52f), dp(34f), paint.getColor());
        paint.setColor(Color.rgb(255, 222, 157));
        canvas.drawRect(crate.left + dp(10f), crate.bottom - dp(7f), crate.right - dp(10f), crate.bottom - dp(3f), paint);
        textPaint.setTextSize(sp(14f));
        textPaint.setColor(CREAM);
        canvas.drawText("STEAK", crate.centerX(), crate.bottom - dp(13f), textPaint);

        paint.setColor(Color.rgb(64, 68, 72));
        canvas.drawRect(trashRect, paint);
        paint.setColor(Color.rgb(35, 38, 42));
        canvas.drawRect(trashRect.left + dp(8f), trashRect.top + dp(10f),
                trashRect.right - dp(8f), trashRect.bottom - dp(8f), paint);
        textPaint.setTextSize(sp(13f));
        textPaint.setColor(CREAM);
        canvas.drawText("TRASH", trashRect.centerX(), trashRect.bottom - dp(13f), textPaint);

        textPaint.setTextSize(sp(14f));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.rgb(255, 239, 199));
        canvas.drawText("SERVED " + engine.getServed(), dp(134f), top + dp(42f), textPaint);
        canvas.drawText("TIME " + Math.round(engine.getElapsed()) + "s", dp(134f), top + dp(66f), textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawDraggedSteak(Canvas canvas) {
        if (draggingPan < 0 || !moved || !hasSteak(draggingPan)) {
            return;
        }
        GameEngine.Steak steak = engine.getPans()[draggingPan].steak;
        drawSteak(canvas, dragX, dragY, dp(72f), steak);
        drawDraggedCookBadge(canvas, dragX, dragY + dp(52f), steak);
    }

    private void drawDraggedCookBadge(Canvas canvas, float cx, float top, GameEngine.Steak steak) {
        String title = engine.describeSteak(steak);
        String detail = "\u5747" + GameEngine.cookDisplayValue(steak.averageCook())
                + "  \u6a59" + GameEngine.cookDisplayValue(steak.bottomCook)
                + "  \u84dd" + GameEngine.cookDisplayValue(steak.topCook);
        float width = Math.min(getWidth() - dp(28f), dp(190f));
        float height = dp(48f);
        float left = Math.max(dp(14f), Math.min(getWidth() - dp(14f) - width, cx - width * 0.5f));
        float adjustedTop = Math.max(dp(70f), Math.min(getHeight() - dp(122f) - height, top));
        RectF badge = new RectF(left, adjustedTop, left + width, adjustedTop + height);

        paint.setColor(Color.argb(230, 31, 34, 35));
        canvas.drawRect(badge, paint);
        paint.setColor(GOLD);
        canvas.drawRect(badge.left, badge.top, badge.right, badge.top + dp(4f), paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(CREAM);
        textPaint.setTextSize(sp(15f));
        canvas.drawText(title, badge.centerX(), badge.top + dp(20f), textPaint);
        textPaint.setTextSize(sp(13f));
        canvas.drawText(detail, badge.centerX(), badge.top + dp(37f), textPaint);
    }

    private void drawToast(Canvas canvas) {
        if (toastTimer <= 0f || toast.length() == 0) {
            return;
        }
        float alpha = Math.min(1f, toastTimer / 0.35f);
        float maxWidth = getWidth() - dp(24f);
        textPaint.setColor(Color.argb((int) (alpha * 255f), 255, 245, 198));
        if (toastDetail.length() > 0) {
            drawFittedCenteredText(canvas, toast, getWidth() * 0.5f, getHeight() * 0.525f,
                    sp(26f), sp(15f), maxWidth);
            drawFittedCenteredText(canvas, toastDetail, getWidth() * 0.5f, getHeight() * 0.555f,
                    sp(16f), sp(12f), maxWidth);
        } else {
            drawFittedCenteredText(canvas, toast, getWidth() * 0.5f, getHeight() * 0.54f,
                    sp(26f), sp(14f), maxWidth);
        }
    }

    private void drawFittedCenteredText(Canvas canvas, String text, float x, float y,
            float startSize, float minSize, float maxWidth) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        float size = startSize;
        textPaint.setTextSize(size);
        while (textPaint.measureText(text) > maxWidth && size > minSize) {
            size -= dp(1f);
            textPaint.setTextSize(size);
        }
        canvas.drawText(text, x, y, textPaint);
    }

    private void drawTutorial(Canvas canvas) {
        if (!showTutorial) {
            return;
        }

        paint.setColor(Color.argb(225, 19, 22, 22));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        float margin = dp(26f);
        float cardTop = dp(150f);
        float cardBottom = Math.min(getHeight() - dp(140f), cardTop + dp(620f));
        RectF card = temp;
        card.set(margin, cardTop, getWidth() - margin, cardBottom);
        paint.setColor(Color.rgb(244, 220, 166));
        canvas.drawRect(card, paint);
        paint.setColor(Color.rgb(96, 58, 42));
        canvas.drawRect(card.left, card.bottom - dp(8f), card.right, card.bottom, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(28f));
        textPaint.setColor(INK);
        canvas.drawText("\u5f00\u5e97\u6559\u5b66", card.centerX(), card.top + dp(44f), textPaint);

        float y = card.top + dp(88f);
        y = drawTutorialLine(canvas, "\u70b9\u7a7a\u714e\u9505\uff1a\u653e\u4e00\u5757\u65b0\u725b\u6392\u3002", y);
        y = drawTutorialLine(canvas, "\u70b9\u6b63\u5728\u714e\u7684\u725b\u6392\uff1a\u7ffb\u9762\u3002", y);
        y = drawTutorialLine(canvas, "\u725b\u6392\u6807\u7b7e\u548c\u5ba2\u4eba\u8981\u6c42\u4e00\u81f4\u65f6\uff0c\u62d6\u5230\u5ba2\u4eba\u51fa\u9910\u3002", y);
        y = drawTutorialLine(canvas, "\u5927\u6761=\u7efc\u5408\u719f\u5ea6\uff1b\u6a59/\u84dd\u5c0f\u6761=\u4e24\u9762\u719f\u5ea6\u3002", y);
        y = drawTutorialLine(canvas, "\u7ffb\u9762\u66f4\u5747\u5300\u4f1a\u52a0\u5206\uff0c\u4e0d\u4f1a\u56e0\u4e3a\u4e24\u9762\u4e0d\u5747\u800c\u76f4\u63a5\u5931\u8d25\u3002", y);

        drawTutorialExample(canvas, card.centerX(), y + dp(72f));

        tutorialButtonRect.set(card.left + dp(46f), card.bottom - dp(78f),
                card.right - dp(46f), card.bottom - dp(26f));
        paint.setColor(GREEN);
        canvas.drawRect(tutorialButtonRect, paint);
        paint.setColor(Color.rgb(45, 105, 65));
        canvas.drawRect(tutorialButtonRect.left, tutorialButtonRect.bottom - dp(5f),
                tutorialButtonRect.right, tutorialButtonRect.bottom, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(23f));
        textPaint.setColor(Color.WHITE);
        canvas.drawText("\u5f00\u59cb\u8425\u4e1a", tutorialButtonRect.centerX(),
                tutorialButtonRect.top + dp(34f), textPaint);
    }

    private float drawTutorialLine(Canvas canvas, String text, float y) {
        float left = dp(48f);
        float maxWidth = getWidth() - dp(96f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(17f));
        textPaint.setColor(INK);
        if (textPaint.measureText(text) <= maxWidth) {
            canvas.drawText(text, left, y, textPaint);
            return y + dp(30f);
        }

        int start = 0;
        while (start < text.length()) {
            int count = textPaint.breakText(text, start, text.length(), true, maxWidth, null);
            canvas.drawText(text, start, start + count, left, y, textPaint);
            start += count;
            y += dp(25f);
        }
        return y + dp(8f);
    }

    private void drawTutorialExample(Canvas canvas, float cx, float cy) {
        float w = dp(240f);
        float h = dp(98f);
        RectF example = new RectF(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f);
        paint.setColor(Color.rgb(54, 58, 62));
        canvas.drawRect(example, paint);
        paint.setColor(PAN_EDGE);
        canvas.drawOval(example.left + dp(14f), example.top + dp(12f),
                example.left + dp(104f), example.bottom - dp(12f), paint);
        GameEngine.Steak steak = new GameEngine.Steak();
        steak.bottomCook = 38f;
        steak.topCook = 24f;
        drawSteak(canvas, example.left + dp(59f), example.centerY(), dp(50f), steak);

        float barLeft = example.left + dp(122f);
        float barRight = example.right - dp(16f);
        float barTop = example.top + dp(22f);
        paint.setColor(Color.rgb(32, 34, 35));
        canvas.drawRect(barLeft, barTop, barRight, barTop + dp(9f), paint);
        paint.setColor(GREEN);
        canvas.drawRect(barLeft, barTop, barLeft + (barRight - barLeft) * GameEngine.cookFraction(43f),
                barTop + dp(9f), paint);
        drawSideCookBar(canvas, barLeft, barTop + dp(16f), barRight, 38f,
                Color.rgb(242, 116, 64));
        drawSideCookBar(canvas, barLeft, barTop + dp(24f), barRight, 24f,
                Color.rgb(96, 178, 225));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(14f));
        textPaint.setColor(CREAM);
        canvas.drawText("\u63a5\u8fd1\u4e94\u5206\u719f", barLeft, example.bottom - dp(18f), textPaint);
    }

    private void drawSteak(Canvas canvas, float cx, float cy, float size, GameEngine.Steak steak) {
        float cook = GameEngine.cookFraction(steak.averageCook());
        int red = (int) (202 - cook * 78);
        int green = (int) (65 + cook * 62);
        int blue = (int) (59 - cook * 31);
        int steakColor = steak.isBurned() ? Color.rgb(57, 38, 26) : Color.rgb(red, green, Math.max(24, blue));
        int edge = steak.isBurned() ? Color.rgb(25, 20, 18) : Color.rgb(112, 48, 39);

        float block = size / 5f;
        paint.setColor(edge);
        drawPixelBlock(canvas, cx - block * 2.2f, cy - block * 1.5f, block * 4.4f, block * 3.0f, edge);
        drawPixelBlock(canvas, cx - block * 1.8f, cy - block * 1.1f, block * 3.6f, block * 2.2f, steakColor);
        drawPixelBlock(canvas, cx - block * 0.4f, cy - block * 1.25f, block * 1.0f, block * 0.55f,
                Color.rgb(248, 213, 155));
        drawPixelBlock(canvas, cx - block * 1.4f, cy + block * 0.55f, block * 1.0f, block * 0.45f,
                Color.rgb(88, 38, 32));
        drawPixelBlock(canvas, cx + block * 0.7f, cy + block * 0.40f, block * 0.9f, block * 0.40f,
                Color.rgb(88, 38, 32));

        if (steak.unevenness() > 28f) {
            paint.setColor(Color.argb(190, 255, 229, 88));
            canvas.drawRect(cx - block * 2.1f, cy - block * 2.15f,
                    cx + block * 2.1f, cy - block * 1.75f, paint);
        }
    }

    private void drawPixelBlock(Canvas canvas, float left, float top, float width, float height, int color) {
        paint.setColor(color);
        canvas.drawRect(left, top, left + width, top + height, paint);
        paint.setColor(adjust(color, 34));
        canvas.drawRect(left, top, left + width, top + Math.max(2f, height * 0.16f), paint);
        paint.setColor(adjust(color, -42));
        canvas.drawRect(left, top + height * 0.82f, left + width, top + height, paint);
        canvas.drawRect(left + width * 0.84f, top, left + width, top + height, paint);
    }

    private int adjust(int color, int delta) {
        return Color.rgb(
                clamp(Color.red(color) + delta),
                clamp(Color.green(color) + delta),
                clamp(Color.blue(color) + delta));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int findPan(float x, float y) {
        for (int i = 0; i < panRects.length; i++) {
            if (panRects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private int findCustomer(float x, float y) {
        List<GameEngine.Customer> customers = engine.getCustomers();
        for (int i = 0; i < customers.size() && i < customerRects.length; i++) {
            if (customerRects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasSteak(int pan) {
        return pan >= 0 && pan < GameEngine.PAN_COUNT && engine.getPans()[pan].steak != null;
    }

    private int cookingPausedPan() {
        if (draggingPan >= 0 && moved && hasSteak(draggingPan)) {
            return draggingPan;
        }
        return -1;
    }

    private void showToast(String text) {
        toast = text;
        toastDetail = "";
        toastTimer = 0.9f;
    }

    private void showToast(String text, String detail) {
        toast = text;
        toastDetail = detail == null ? "" : detail;
        toastTimer = 1.25f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

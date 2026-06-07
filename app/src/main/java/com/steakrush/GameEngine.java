package com.steakrush;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameEngine {
    public static final int PAN_COUNT = 4;
    public static final int MAX_CUSTOMERS = 4;
    public static final float DISPLAY_COOK_MAX = 100f;

    private static final float SIDE_COOK_RATE = 22f;
    private static final float MAX_SIDE_COOK = 118f;
    private static final float BURNED_SIDE_COOK = 110f;
    private static final float BURNED_AVERAGE_COOK = 98f;
    private static final float START_SPAWN_INTERVAL = 4.8f;
    private static final float MIN_SPAWN_INTERVAL = 2.25f;
    private static final float GAME_SPEED = 0.5f;
    private static final float INITIAL_SPAWN_TIMER = 2.1f;

    public enum Doneness {
        RARE("\u4e00\u5206\u719f", 16f, 11f),
        MEDIUM_RARE("\u4e09\u5206\u719f", 29f, 12f),
        MEDIUM("\u4e94\u5206\u719f", 43f, 13f),
        MEDIUM_WELL("\u4e03\u5206\u719f", 58f, 13f),
        WELL_DONE("\u5168\u719f", 73f, 14f);

        public final String label;
        public final float target;
        public final float tolerance;

        Doneness(String label, float target, float tolerance) {
            this.label = label;
            this.target = target;
            this.tolerance = tolerance;
        }
    }

    public static final class Steak {
        public float topCook;
        public float bottomCook;
        public boolean flipped;
        public int flips;
        public float age;

        public float averageCook() {
            return (topCook + bottomCook) * 0.5f;
        }

        public float unevenness() {
            return Math.abs(topCook - bottomCook);
        }

        public boolean isBurned() {
            return topCook >= BURNED_SIDE_COOK
                    || bottomCook >= BURNED_SIDE_COOK
                    || averageCook() >= BURNED_AVERAGE_COOK;
        }
    }

    public static final class Pan {
        public Steak steak;
    }

    public static final class Customer {
        public final int id;
        public final Doneness doneness;
        public final float patience;
        public float wait;

        private Customer(int id, Doneness doneness, float patience) {
            this.id = id;
            this.doneness = doneness;
            this.patience = patience;
        }

        public float patienceFraction() {
            return Math.max(0f, Math.min(1f, 1f - wait / patience));
        }
    }

    public static final class GameEvent {
        public static final int CUSTOMER_ARRIVED = 1;
        public static final int CUSTOMER_LEFT = 2;

        public final int type;
        public final Doneness doneness;

        private GameEvent(int type, Doneness doneness) {
            this.type = type;
            this.doneness = doneness;
        }
    }

    public static final class ServeResult {
        public boolean valid;
        public boolean success;
        public boolean perfect;
        public Doneness requested;
        public float actual;
        public float topCook;
        public float bottomCook;
        public int points;
        public String message;
    }

    private final Random random = new Random(88731L);
    private final Pan[] pans = new Pan[PAN_COUNT];
    private final ArrayList<Customer> customers = new ArrayList<>();
    private final ArrayList<GameEvent> events = new ArrayList<>();

    private float elapsed;
    private float spawnTimer;
    private int nextCustomerId = 1;
    private int score;
    private int served;
    private int missed;
    private int streak;

    public GameEngine() {
        for (int i = 0; i < pans.length; i++) {
            pans[i] = new Pan();
        }
        reset();
    }

    public void reset() {
        random.setSeed(88731L);
        for (Pan pan : pans) {
            pan.steak = null;
        }
        customers.clear();
        events.clear();
        elapsed = 0f;
        spawnTimer = INITIAL_SPAWN_TIMER;
        nextCustomerId = 1;
        score = 0;
        served = 0;
        missed = 0;
        streak = 0;
        spawnCustomer();
        spawnTimer = INITIAL_SPAWN_TIMER;
    }

    public void update(float deltaSeconds) {
        update(deltaSeconds, -1);
    }

    public void update(float deltaSeconds, int pausedPanIndex) {
        float dt = Math.max(0f, Math.min(0.05f, deltaSeconds)) * GAME_SPEED;
        elapsed += dt;

        for (int i = 0; i < pans.length; i++) {
            Pan pan = pans[i];
            if (pan.steak != null && i != pausedPanIndex) {
                updateSteak(pan.steak, dt);
            }
        }

        for (int i = customers.size() - 1; i >= 0; i--) {
            Customer customer = customers.get(i);
            customer.wait += dt;
            if (customer.wait >= customer.patience) {
                customers.remove(i);
                missed++;
                streak = 0;
                events.add(new GameEvent(GameEvent.CUSTOMER_LEFT, customer.doneness));
            }
        }

        spawnTimer -= dt;
        if (spawnTimer <= 0f) {
            if (customers.size() < MAX_CUSTOMERS) {
                spawnCustomer();
            }
            spawnTimer = nextSpawnInterval();
        }
    }

    public List<GameEvent> drainEvents() {
        ArrayList<GameEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    public Pan[] getPans() {
        return pans;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public int getScore() {
        return score;
    }

    public int getServed() {
        return served;
    }

    public int getMissed() {
        return missed;
    }

    public int getStreak() {
        return streak;
    }

    public float getElapsed() {
        return elapsed;
    }

    public boolean placeSteak(int panIndex) {
        if (!isValidPan(panIndex) || pans[panIndex].steak != null) {
            return false;
        }
        pans[panIndex].steak = new Steak();
        return true;
    }

    public boolean flipSteak(int panIndex) {
        if (!isValidPan(panIndex) || pans[panIndex].steak == null) {
            return false;
        }
        Steak steak = pans[panIndex].steak;
        steak.flipped = !steak.flipped;
        steak.flips++;
        return true;
    }

    public boolean discardSteak(int panIndex) {
        if (!isValidPan(panIndex) || pans[panIndex].steak == null) {
            return false;
        }
        pans[panIndex].steak = null;
        streak = 0;
        score = Math.max(0, score - 8);
        return true;
    }

    public ServeResult serve(int panIndex, int customerIndex) {
        ServeResult result = new ServeResult();
        if (!isValidPan(panIndex)
                || panIndex < 0
                || customerIndex < 0
                || customerIndex >= customers.size()
                || pans[panIndex].steak == null) {
            result.valid = false;
            result.message = "";
            return result;
        }

        result.valid = true;
        Steak steak = pans[panIndex].steak;
        Customer customer = customers.get(customerIndex);
        float average = steak.averageCook();
        float gap = Math.abs(average - customer.doneness.target);
        float unevenness = steak.unevenness();
        boolean balanced = unevenness <= 34f;
        boolean inWindow = gap <= customer.doneness.tolerance;
        boolean notBurned = !steak.isBurned();

        result.requested = customer.doneness;
        result.actual = average;
        result.topCook = steak.topCook;
        result.bottomCook = steak.bottomCook;
        result.success = inWindow && notBurned;
        result.perfect = result.success
                && gap <= customer.doneness.tolerance * 0.45f
                && unevenness <= 22f
                && steak.flips > 0;

        if (result.success) {
            streak++;
            served++;
            int patienceBonus = Math.round(customer.patienceFraction() * 35f);
            int streakBonus = Math.min(80, streak * 8);
            int balanceBonus = balanced ? 18 : 0;
            int perfectBonus = result.perfect ? 45 : 0;
            result.points = 90 + patienceBonus + streakBonus + balanceBonus + perfectBonus;
            score += result.points;
            result.message = result.perfect ? "PERFECT" : (balanced ? "GOOD" : "OK");
        } else {
            streak = 0;
            missed++;
            result.points = -30;
            score = Math.max(0, score + result.points);
            result.message = steak.isBurned() ? "BURNT" : (average < customer.doneness.target ? "TOO RAW" : "OVER");
        }

        pans[panIndex].steak = null;
        customers.remove(customerIndex);
        return result;
    }

    public String describeSteak(Steak steak) {
        if (steak == null) {
            return "";
        }
        if (steak.isBurned()) {
            return "BURNT";
        }
        return describeCookValue(steak.averageCook());
    }

    public String describeCookValue(float cook) {
        Doneness closest = Doneness.RARE;
        float bestGap = Float.MAX_VALUE;
        for (Doneness doneness : Doneness.values()) {
            float gap = Math.abs(cook - doneness.target);
            if (gap < bestGap) {
                bestGap = gap;
                closest = doneness;
            }
        }
        if (cook < Doneness.RARE.target - Doneness.RARE.tolerance) {
            return "RAW";
        }
        if (cook > Doneness.WELL_DONE.target + Doneness.WELL_DONE.tolerance) {
            return "OVER";
        }
        return closest.label;
    }

    public static float cookFraction(float cook) {
        return Math.max(0f, Math.min(1f, cook / DISPLAY_COOK_MAX));
    }

    public static String cookDisplayValue(float cook) {
        int rounded = Math.max(0, Math.round(cook));
        return rounded > Math.round(DISPLAY_COOK_MAX) ? Math.round(DISPLAY_COOK_MAX) + "+" : String.valueOf(rounded);
    }

    private void updateSteak(Steak steak, float dt) {
        steak.age += dt;
        if (steak.flipped) {
            steak.topCook = Math.min(MAX_SIDE_COOK, steak.topCook + SIDE_COOK_RATE * dt);
        } else {
            steak.bottomCook = Math.min(MAX_SIDE_COOK, steak.bottomCook + SIDE_COOK_RATE * dt);
        }
    }

    private void spawnCustomer() {
        Doneness[] values = Doneness.values();
        int maxIndex;
        if (elapsed < 35f) {
            maxIndex = 2;
        } else if (elapsed < 80f) {
            maxIndex = 3;
        } else {
            maxIndex = values.length - 1;
        }
        Doneness doneness = values[random.nextInt(maxIndex + 1)];
        float patience = Math.max(12.5f, 22.0f - elapsed * 0.030f);
        customers.add(new Customer(nextCustomerId++, doneness, patience));
        events.add(new GameEvent(GameEvent.CUSTOMER_ARRIVED, doneness));
    }

    private float nextSpawnInterval() {
        float base = START_SPAWN_INTERVAL - elapsed * 0.018f;
        float jitter = random.nextFloat() * 0.65f;
        return Math.max(MIN_SPAWN_INTERVAL, base) + jitter;
    }

    private boolean isValidPan(int panIndex) {
        return panIndex >= 0 && panIndex < pans.length;
    }
}

package com.munna.dc_calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;

/**
 * DC Calculator Native Android edition.
 * The calculator UI and expression engine are native Java; no WebView or HTML UI is used.
 */
public final class MainActivity extends Activity {
    private static final String PREFS = "dc_calculator_native";
    private static final String HISTORY_KEY = "history_v1";
    private static final String THEME_KEY = "dark_v1";
    private static final String MEMORY_VALUE_KEY = "memory_value_v1";
    private static final String MEMORY_SET_KEY = "memory_set_v1";
    private int bg, panel, key, text, muted, accent, accentText, danger;
    private boolean dark = false, scientific = false, memorySet = false;
    private double memory = 0d;
    private String expression = "";
    private String lastShownExpression = "";
    private String result = "0";
    private boolean justEvaluated = false;
    private LinearLayout page, scientificKeys;
    private TextView expressionView, resultView, memoryView, vipStatusText;
    private Button vipMonthlyButton, vipYearlyButton;
    private AlertDialog vipDialog;
    private final ArrayList<String> history = new ArrayList<>();
    private VipBillingManager billing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = getPreferences(MODE_PRIVATE).getBoolean(THEME_KEY, false);
        memory = Double.longBitsToDouble(getPreferences(MODE_PRIVATE).getLong(MEMORY_VALUE_KEY, Double.doubleToRawLongBits(0d)));
        memorySet = getPreferences(MODE_PRIVATE).getBoolean(MEMORY_SET_KEY, false);
        loadHistory();
        if (state != null) restoreUiState(state);
        applySystemBars();
        billing = new VipBillingManager(this, this::handleBillingStatus);
        buildUi();
    }

    private void applyPalette() {
        if (dark) {
            bg = Color.rgb(15, 21, 34); panel = Color.rgb(27, 36, 54);
            key = Color.rgb(39, 50, 72); text = Color.rgb(241, 245, 255);
            muted = Color.rgb(164, 177, 201); accent = Color.rgb(100, 111, 255);
            accentText = Color.WHITE; danger = Color.rgb(176, 65, 85);
        } else {
            bg = Color.rgb(239, 242, 249); panel = Color.WHITE;
            key = Color.rgb(229, 234, 244); text = Color.rgb(28, 36, 54);
            muted = Color.rgb(103, 115, 138); accent = Color.rgb(83, 100, 238);
            accentText = Color.WHITE; danger = Color.rgb(194, 58, 80);
        }
    }

    private void applySystemBars() {
        Window w = getWindow();
        int color = dark ? Color.rgb(15, 21, 34) : Color.rgb(239, 242, 249);
        w.setStatusBarColor(color);
        w.setNavigationBarColor(color);
        w.getDecorView().setSystemUiVisibility(dark ? 0 :
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void buildUi() {
        applyPalette();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = row();
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("DC Calculator", 23, text, true);
        TextView sub = label("NATIVE ANDROID EDITION", 9, muted, true);
        sub.setLetterSpacing(0.14f);
        brand.addView(title);
        brand.addView(sub);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button theme = button(dark ? "☀" : "☾", key, text, 13);
        theme.setContentDescription("Change appearance");
        header.addView(theme, new LinearLayout.LayoutParams(dp(48), dp(44)));
        theme.setOnClickListener(v -> {
            dark = !dark;
            getPreferences(MODE_PRIVATE).edit().putBoolean(THEME_KEY, dark).apply();
            applySystemBars();
            buildUi();
        });
        page.addView(header);

        LinearLayout shortcuts = row();
        addSmallAction(shortcuts, "Scientific", () -> { scientific = !scientific; buildUi(); });
        addSmallAction(shortcuts, "History", this::showHistory);
        addSmallAction(shortcuts, "Student", this::showStudentTool);
        addSmallAction(shortcuts, "VIP", this::showVip);
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(-1, dp(42));
        shortcutParams.topMargin = dp(12);
        page.addView(shortcuts, shortcutParams);

        LinearLayout display = new LinearLayout(this);
        display.setOrientation(LinearLayout.VERTICAL);
        display.setPadding(dp(18), dp(16), dp(18), dp(16));
        display.setBackground(rounded(panel, 23));
        TextView expressionLabel = label("EXPRESSION", 9, muted, true);
        expressionLabel.setLetterSpacing(.15f);
        display.addView(expressionLabel);
        expressionView = label(lastShownExpression.isEmpty() ? (expression.isEmpty() ? "0" : expression) : lastShownExpression,
            17, muted, false);
        expressionView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        expressionView.setSingleLine(true);
        expressionView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(35));
        ep.topMargin = dp(3);
        display.addView(expressionView, ep);
        resultView = label(result, 36, text, true);
        resultView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resultView.setSingleLine(true);
        resultView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        display.addView(resultView, new LinearLayout.LayoutParams(-1, dp(52)));
        memoryView = label(memorySet ? "M · MEMORY STORED" : "DEG · READY", 9, muted, true);
        memoryView.setGravity(Gravity.END);
        display.addView(memoryView);
        LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(-1, -2);
        displayParams.topMargin = dp(14);
        page.addView(display, displayParams);

        LinearLayout mem = row();
        addKey(mem, "MC", () -> {
            memory = 0; memorySet = false;
            persistMemory();
            updateDisplay();
        }, false, false);
        addKey(mem, "MR", this::recallMemory, false, false);
        addKey(mem, "M+", () -> memoryChange(1), false, false);
        addKey(mem, "M−", () -> memoryChange(-1), false, false);
        addRowToPage(mem, 10);

        scientificKeys = new LinearLayout(this);
        scientificKeys.setOrientation(LinearLayout.VERTICAL);
        scientificKeys.setVisibility(scientific ? View.VISIBLE : View.GONE);
        addKeyRow(scientificKeys, new String[]{"sin", "cos", "tan", "√x"}, new String[]{"sin(", "cos(", "tan(", "sqrt("});
        addKeyRow(scientificKeys, new String[]{"sin⁻¹", "cos⁻¹", "tan⁻¹", "∛x"}, new String[]{"asin(", "acos(", "atan(", "cbrt("});
        addKeyRow(scientificKeys, new String[]{"log", "ln", "|x|", "eˣ"}, new String[]{"log(", "ln(", "abs(", "exp("});
        addKeyRow(scientificKeys, new String[]{"π", "e", "xʸ", "x!"}, new String[]{"pi", "e", "^", "!"});
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(10);
        page.addView(scientificKeys, sp);

        addKeyRow(page, new String[]{"AC", "DEL", "(", ")"}, new String[]{"AC", "DEL", "(", ")"});
        addKeyRow(page, new String[]{"7", "8", "9", "÷"}, new String[]{"7", "8", "9", "/"});
        addKeyRow(page, new String[]{"4", "5", "6", "×"}, new String[]{"4", "5", "6", "*"});
        addKeyRow(page, new String[]{"1", "2", "3", "−"}, new String[]{"1", "2", "3", "-"});
        addKeyRow(page, new String[]{"±", "0", ".", "+"}, new String[]{"±", "0", ".", "+"});
        addKeyRow(page, new String[]{"%", "√", "x²", "="}, new String[]{"%", "sqrt(", "^2", "="});

        TextView footer = label("Calculation history is saved on this device", 10, muted, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(30));
        fp.topMargin = dp(10);
        page.addView(footer, fp);

        setContentView(scroll);
        updateDisplay();
    }

    private void addSmallAction(LinearLayout parent, String title, Runnable action) {
        Button b = button(title, panel, text, 10);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(39), 1);
        p.setMargins(dp(2), dp(1), dp(2), dp(1));
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    private void addKeyRow(LinearLayout parent, String[] labels, String[] values) {
        LinearLayout r = row();
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            int color = key;
            int fg = text;
            boolean bold = true;
            if ("=".equals(value)) { color = accent; fg = accentText; }
            else if ("AC".equals(value)) { color = danger; fg = Color.WHITE; }
            else if (isOperator(value) || value.endsWith("(") || "pi".equals(value) || "e".equals(value)) {
                color = dark ? Color.rgb(46, 51, 89) : Color.rgb(225, 229, 255);
                fg = dark ? Color.rgb(207, 210, 255) : Color.rgb(67, 78, 192);
            }
            Button b = button(labels[i], color, fg, labels[i].length() > 3 ? 11 : 16);
            b.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(53), 1);
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            r.addView(b, p);
            b.setOnClickListener(v -> onKey(value));
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(2);
        parent.addView(r, p);
    }

    private void addKey(LinearLayout parent, String title, Runnable action, boolean emphasis, boolean warn) {
        Button b = button(title, emphasis ? accent : (warn ? danger : key), emphasis ? accentText : (warn ? Color.WHITE : text), 12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
        p.setMargins(dp(3), dp(2), dp(3), dp(2));
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    private void addRowToPage(LinearLayout r, int marginTop) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(marginTop);
        page.addView(r, p);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value, int color, int foreground, float size) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(foreground);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackground(rounded(color, 15));
        b.setElevation(dp(1));
        return b;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private boolean isOperator(String v) {
        return "+".equals(v) || "-".equals(v) || "*".equals(v) || "/".equals(v) || "^".equals(v);
    }

    private void onKey(String keyValue) {
        if ("AC".equals(keyValue)) {
            expression = "";
            lastShownExpression = "";
            result = "0";
            justEvaluated = false;
        } else if ("DEL".equals(keyValue)) {
            if (justEvaluated) { expression = result; justEvaluated = false; }
            if (!expression.isEmpty()) expression = expression.substring(0, expression.length() - 1);
            lastShownExpression = "";
        } else if ("=".equals(keyValue)) {
            evaluateNow();
            return;
        } else if ("±".equals(keyValue)) {
            if (justEvaluated) { expression = result; justEvaluated = false; }
            if (expression.startsWith("-(") && expression.endsWith(")")) expression = expression.substring(2, expression.length() - 1);
            else if (!expression.isEmpty()) expression = "-(" + expression + ")";
            else expression = "-";
            lastShownExpression = "";
        } else {
            if (justEvaluated) {
                boolean continuation = isOperator(keyValue) || "%".equals(keyValue) || "!".equals(keyValue) || "^2".equals(keyValue);
                expression = continuation ? result : "";
                justEvaluated = false;
            }
            appendToken(keyValue);
            lastShownExpression = "";
        }
        result = expression.isEmpty() ? "0" : expression;
        updateDisplay();
    }

    private void appendToken(String token) {
        if (token == null || token.isEmpty()) return;
        if ("^2".equals(token)) {
            if (!endsWithOperand(expression)) {
                Toast.makeText(this, "Enter a number before squaring it.", Toast.LENGTH_SHORT).show();
                return;
            }
            expression += token;
            return;
        }
        if (")".equals(token)) {
            if (unmatchedOpeningParenthesis(expression) > 0 && endsWithOperand(expression)) expression += token;
            else Toast.makeText(this, "There is no open parenthesis to close.", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("!".equals(token) || "%".equals(token)) {
            if (endsWithOperand(expression)) expression += token;
            else Toast.makeText(this, "Enter a number before using " + token + ".", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isOperator(token)) {
            if (expression.isEmpty()) {
                if ("-".equals(token) || "+".equals(token)) expression = token;
                else Toast.makeText(this, "Enter a number first.", Toast.LENGTH_SHORT).show();
                return;
            }
            char last = expression.charAt(expression.length() - 1);
            if (isOperator(String.valueOf(last))) {
                if ("-".equals(token) && (last == '*' || last == '/' || last == '^')) expression += token;
                else expression = expression.substring(0, expression.length() - 1) + token;
            } else expression += token;
            return;
        }
        if ("(".equals(token)) {
            if (endsWithOperand(expression)) expression += "*";
            expression += token;
            return;
        }
        if ("pi".equals(token) || "e".equals(token) || token.endsWith("(")) {
            if (endsWithOperand(expression)) expression += "*";
            expression += token;
            return;
        }
        if (token.matches("[0-9]")) {
            if (endsWithClosingOperand(expression)) expression += "*";
            expression += token;
            return;
        }
        if (".".equals(token)) {
            if (endsWithClosingOperand(expression)) expression += "*";
            if (currentNumberHasDecimal(expression)) {
                Toast.makeText(this, "This number already has a decimal point.", Toast.LENGTH_SHORT).show();
                return;
            }
            expression += token;
            return;
        }
        expression += token;
    }

    private boolean endsWithOperand(String value) {
        if (value == null || value.isEmpty()) return false;
        String s = value.trim();
        return endsWithNumber(s) || endsWithClosingOperand(s) || s.endsWith("pi") || s.endsWith("e");
    }

    private boolean endsWithNumber(String value) {
        if (value == null || value.isEmpty()) return false;
        char last = value.charAt(value.length() - 1);
        return Character.isDigit(last) || last == '.';
    }

    private boolean endsWithClosingOperand(String value) {
        if (value == null || value.isEmpty()) return false;
        char last = value.charAt(value.length() - 1);
        return last == ')' || last == '!' || value.endsWith("pi") || value.endsWith("e");
    }

    private boolean currentNumberHasDecimal(String value) {
        if (value == null || value.isEmpty()) return false;
        int i = value.length() - 1;
        while (i >= 0) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch) || ch == '.') { if (ch == '.') return true; i--; continue; }
            break;
        }
        return false;
    }

    private int unmatchedOpeningParenthesis(String value) {
        int count = 0;
        if (value == null) return 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '(') count++;
            else if (value.charAt(i) == ')') count--;
        }
        return Math.max(0, count);
    }

    private void recallMemory() {
        String memoryToken = "(" + fmt(memory) + ")";
        if (justEvaluated) { expression = ""; justEvaluated = false; }
        if (endsWithOperand(expression)) expression += "*";
        expression += memoryToken;
        lastShownExpression = "";
        result = expression;
        updateDisplay();
    }

    private void persistMemory() {
        getPreferences(MODE_PRIVATE).edit()
            .putLong(MEMORY_VALUE_KEY, Double.doubleToRawLongBits(memory))
            .putBoolean(MEMORY_SET_KEY, memorySet)
            .apply();
    }

    private void evaluateNow() {
        if (expression.trim().isEmpty()) return;
        try {
            double answer = new ExpressionParser(expression).parse();
            if (!Double.isFinite(answer)) throw new IllegalArgumentException("Result is outside the supported range.");
            lastShownExpression = expression + " =";
            result = fmt(answer);
            history.add(0, expression + " = " + result);
            while (history.size() > 100) history.remove(history.size() - 1);
            saveHistory();
            expression = result.replace(",", "");
            justEvaluated = true;
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage() == null ? "Check your expression." : e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        updateDisplay();
    }

    private String fmt(double n) {
        if (!Double.isFinite(n)) return "Error";
        if (Math.abs(n) < 1.0e12 && (n == 0 || Math.abs(n) >= 1.0e-8)) {
            return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();
        }
        return String.format(Locale.US, "%.8g", n);
    }

    private void updateDisplay() {
        if (expressionView != null) expressionView.setText(lastShownExpression.isEmpty() ? (expression.isEmpty() ? "0" : expression) : lastShownExpression);
        if (resultView != null) resultView.setText(result);
        if (memoryView != null) memoryView.setText(memorySet ? "M · MEMORY STORED" : "DEG · READY");
    }

    private void memoryChange(int sign) {
        try {
            double value = new ExpressionParser(expression.isEmpty() ? result : expression).parse();
            memory += sign * value;
            memorySet = true;
            persistMemory();
            updateDisplay();
            Toast.makeText(this, "Memory updated", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Calculate a valid value first.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHistory() {
        if (history.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Calculation history").setMessage("No calculations yet.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = history.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Calculation history")
            .setItems(items, (dialog, which) -> {
                String item = history.get(which);
                int eq = item.lastIndexOf(" = ");
                String exp = eq > 0 ? item.substring(0, eq) : item;
                String answer = eq > 0 ? item.substring(eq + 3) : exp;
                expression = exp;
                lastShownExpression = exp + " =";
                result = answer;
                justEvaluated = true;
                updateDisplay();
            })
            .setNeutralButton("Clear history", (d, w) -> {
                history.clear(); saveHistory();
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
            }).setPositiveButton("Close", null).show();
    }

    private void showStudentTool() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText scored = new EditText(this);
        scored.setHint("Marks scored");
        scored.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText total = new EditText(this);
        total.setHint("Total marks");
        total.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = label("Percentage will appear here.", 14, text, true);
        layout.addView(scored); layout.addView(total);
        Button calc = button("Calculate percentage", accent, accentText, 13);
        layout.addView(calc, new LinearLayout.LayoutParams(-1, dp(46)));
        layout.addView(output);
        calc.setOnClickListener(v -> {
            try {
                double m = Double.parseDouble(scored.getText().toString().trim());
                double t = Double.parseDouble(total.getText().toString().trim());
                if (t <= 0 || m < 0 || m > t) throw new IllegalArgumentException();
                output.setText(String.format(Locale.US, "Percentage: %.2f%%", (m/t)*100));
            } catch (RuntimeException ex) { output.setText("Enter valid marks and total."); }
        });
        new AlertDialog.Builder(this).setTitle("Student percentage tool").setView(layout)
            .setPositiveButton("Done", null).show();
    }

    private void handleBillingStatus(boolean active, String message) {
        if (isFinishing() || isDestroyed()) return;
        if (vipStatusText != null) {
            if (active) vipStatusText.setText("VIP is active on this Google Play account.");
            else if (message != null && (message.toLowerCase(Locale.ROOT).contains("unavailable")
                    || message.toLowerCase(Locale.ROOT).contains("not available")
                    || message.toLowerCase(Locale.ROOT).contains("not configured")
                    || message.toLowerCase(Locale.ROOT).contains("could not"))) {
                vipStatusText.setText(message);
            } else vipStatusText.setText("VIP is Free. Google Play purchases will appear here once configured.");
        }
        updateVipPlanLabels();
    }

    private void updateVipPlanLabels() {
        String monthlyPrice = billing == null ? "" : billing.getPrice(VipBillingManager.MONTHLY);
        String yearlyPrice = billing == null ? "" : billing.getPrice(VipBillingManager.YEARLY);
        if (vipMonthlyButton != null) {
            vipMonthlyButton.setText("Monthly VIP · " + (monthlyPrice.isEmpty() ? "₹99" : monthlyPrice));
        }
        if (vipYearlyButton != null) {
            vipYearlyButton.setText("Yearly VIP · " + (yearlyPrice.isEmpty() ? "₹1,099" : yearlyPrice));
        }
    }

    private void showVip() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(4));
        vipStatusText = label(billing != null && billing.isVipActive()
            ? "VIP is active on this Google Play account."
            : "VIP is Free. Google Play subscriptions must be configured in Play Console before checkout works.",
            13, text, false);
        layout.addView(vipStatusText);
        vipMonthlyButton = button("Monthly VIP · ₹99", accent, accentText, 14);
        vipYearlyButton = button("Yearly VIP · ₹1,099", accent, accentText, 14);
        LinearLayout.LayoutParams monthlyParams = new LinearLayout.LayoutParams(-1, dp(48));
        monthlyParams.topMargin = dp(10);
        LinearLayout.LayoutParams yearlyParams = new LinearLayout.LayoutParams(-1, dp(48));
        yearlyParams.topMargin = dp(10);
        layout.addView(vipMonthlyButton, monthlyParams);
        layout.addView(vipYearlyButton, yearlyParams);
        vipMonthlyButton.setOnClickListener(v -> {
            if (billing != null) billing.launchSubscription(VipBillingManager.MONTHLY);
            else Toast.makeText(this, "Billing is not ready yet.", Toast.LENGTH_LONG).show();
        });
        vipYearlyButton.setOnClickListener(v -> {
            if (billing != null) billing.launchSubscription(VipBillingManager.YEARLY);
            else Toast.makeText(this, "Billing is not ready yet.", Toast.LENGTH_LONG).show();
        });
        updateVipPlanLabels();
        vipDialog = new AlertDialog.Builder(this).setTitle("DC Calculator VIP").setView(layout)
            .setNegativeButton("Close", null).setNeutralButton("Restore purchases", (d,w) -> {
                if (billing != null) billing.refresh();
            }).create();
        vipDialog.setOnDismissListener(d -> {
            vipDialog = null;
            vipStatusText = null;
            vipMonthlyButton = null;
            vipYearlyButton = null;
        });
        vipDialog.show();
    }

    private void loadHistory() {
        String json = getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]");
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) history.add(a.getString(i));
        } catch (Exception ignored) { history.clear(); }
    }

    private void saveHistory() {
        JSONArray a = new JSONArray();
        for (String item : history) a.put(item);
        getPreferences(MODE_PRIVATE).edit().putString(HISTORY_KEY, a.toString()).apply();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("dark", dark);
        outState.putBoolean("scientific", scientific);
        outState.putBoolean("memorySet", memorySet);
        outState.putDouble("memory", memory);
        outState.putString("expression", expression);
        outState.putString("lastShownExpression", lastShownExpression);
        outState.putString("result", result);
        outState.putBoolean("justEvaluated", justEvaluated);
        super.onSaveInstanceState(outState);
    }

    private void restoreUiState(Bundle state) {
        dark = state.getBoolean("dark", dark);
        scientific = state.getBoolean("scientific", false);
        memorySet = state.getBoolean("memorySet", memorySet);
        memory = state.getDouble("memory", memory);
        expression = state.getString("expression", "");
        lastShownExpression = state.getString("lastShownExpression", "");
        result = state.getString("result", "0");
        justEvaluated = state.getBoolean("justEvaluated", false);
    }

    @Override public void onBackPressed() {
        if (scientific) { scientific = false; buildUi(); return; }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (billing != null) { billing.close(); billing = null; }
        super.onDestroy();
    }

    /** Safe recursive-descent parser; no JavaScript eval or arbitrary code execution. */
    static final class ExpressionParser {
        private final String s;
        private int pos;
        ExpressionParser(String source) { s = source.replace("×","*").replace("÷","/").replace("−","-").replace("π","pi").replace("√","sqrt"); }
        double parse() {
            double n = expression();
            spaces();
            if (pos != s.length()) throw new IllegalArgumentException("Unexpected character: " + s.charAt(pos));
            return n;
        }
        private double expression() {
            double n = term();
            while (true) {
                spaces();
                if (take('+')) n += term();
                else if (take('-')) n -= term();
                else return n;
            }
        }
        private double term() {
            double n = unary();
            while (true) {
                spaces();
                if (take('*')) n *= unary();
                else if (take('/')) {
                    double d = unary();
                    if (d == 0) throw new IllegalArgumentException("Cannot divide by zero.");
                    n /= d;
                } else if (pos < s.length() && (s.charAt(pos) == '(' || Character.isLetter(s.charAt(pos)))) {
                    // Support conventional implicit multiplication such as 2(3+4), 2pi and 2sin(30).
                    n *= unary();
                } else return n;
            }
        }
        private double unary() {
            spaces();
            if (take('+')) return unary();
            if (take('-')) return -unary();
            return power();
        }
        private double power() {
            double n = postfix();
            spaces();
            if (take('^')) n = Math.pow(n, unary());
            return n;
        }
        private double postfix() {
            double n = primary();
            while (true) {
                spaces();
                if (take('%')) n /= 100.0;
                else if (take('!')) {
                    if (n < 0 || n != Math.rint(n) || n > 170) throw new IllegalArgumentException("Factorial needs an integer from 0 to 170.");
                    double f = 1;
                    for (int i = 2; i <= (int)n; i++) f *= i;
                    n = f;
                } else return n;
            }
        }
        private double primary() {
            spaces();
            if (take('(')) {
                double n = expression();
                spaces();
                if (!take(')')) throw new IllegalArgumentException("Missing closing parenthesis.");
                return n;
            }
            if (pos >= s.length()) throw new IllegalArgumentException("Enter a complete expression.");
            char c = s.charAt(pos);
            if (Character.isDigit(c) || c == '.') return number();
            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
                String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
                if ("pi".equals(name)) return Math.PI;
                if ("e".equals(name)) return Math.E;
                spaces();
                if (!take('(')) throw new IllegalArgumentException("Function " + name + " needs parentheses.");
                double x = expression();
                spaces();
                if (!take(')')) throw new IllegalArgumentException("Missing ')' after " + name + ".");
                switch (name) {
                    case "sin": return Math.sin(Math.toRadians(x));
                    case "cos": return Math.cos(Math.toRadians(x));
                    case "tan": return Math.tan(Math.toRadians(x));
                    case "asin": return Math.toDegrees(Math.asin(x));
                    case "acos": return Math.toDegrees(Math.acos(x));
                    case "atan": return Math.toDegrees(Math.atan(x));
                    case "sqrt": if (x < 0) throw new IllegalArgumentException("Square root needs a non-negative value."); return Math.sqrt(x);
                    case "cbrt": return Math.cbrt(x);
                    case "log": if (x <= 0) throw new IllegalArgumentException("log needs a positive value."); return Math.log10(x);
                    case "ln": if (x <= 0) throw new IllegalArgumentException("ln needs a positive value."); return Math.log(x);
                    case "abs": return Math.abs(x);
                    case "exp": return Math.exp(x);
                    case "floor": return Math.floor(x);
                    case "ceil": return Math.ceil(x);
                    case "round": return Math.rint(x);
                    default: throw new IllegalArgumentException("Unknown function: " + name);
                }
            }
            throw new IllegalArgumentException("Unexpected character: " + c);
        }
        private double number() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos)=='.')) pos++;
            if (pos < s.length() && (s.charAt(pos)=='e' || s.charAt(pos)=='E')) {
                pos++;
                if (pos < s.length() && (s.charAt(pos)=='+' || s.charAt(pos)=='-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            try { return Double.parseDouble(s.substring(start,pos)); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid number."); }
        }
        private void spaces() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        private boolean take(char c) { if (pos < s.length() && s.charAt(pos)==c) { pos++; return true; } return false; }
    }
}

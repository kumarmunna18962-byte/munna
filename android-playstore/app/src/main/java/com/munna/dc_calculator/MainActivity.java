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
import android.widget.HorizontalScrollView;
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
    private int bg, panel, key, text, muted, accent, accentText, danger;
    private boolean dark = false, scientific = false, memorySet = false;
    private double memory = 0d, lastAnswer = 0d;
    private String expression = "";
    private String lastShownExpression = "";
    private String result = "0";
    private boolean justEvaluated = false;
    private LinearLayout page, scientificKeys;
    private TextView expressionView, resultView, memoryView;
    private final ArrayList<String> history = new ArrayList<>();
    private VipBillingManager billing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = getPreferences(MODE_PRIVATE).getBoolean(THEME_KEY, false);
        loadHistory();
        applySystemBars();
        billing = new VipBillingManager(this, (active, message) ->
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show()));
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
        addKey(mem, "MC", () -> { memory = 0; memorySet = false; updateDisplay(); }, false, false);
        addKey(mem, "MR", () -> { expression += fmt(memory); result = fmt(memory); justEvaluated = false; updateDisplay(); }, false, false);
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
            expression = ""; lastShownExpression = ""; result = "0"; justEvaluated = false;
        } else if ("DEL".equals(keyValue)) {
            if (justEvaluated) { expression = result; justEvaluated = false; }
            if (!expression.isEmpty()) expression = expression.substring(0, expression.length() - 1);
            lastShownExpression = "";
        } else if ("=".equals(keyValue)) {
            evaluateNow(); return;
        } else if ("±".equals(keyValue)) {
            if (justEvaluated) { expression = result; justEvaluated = false; }
            if (expression.startsWith("-(") && expression.endsWith(")")) expression = expression.substring(2, expression.length()-1);
            else if (!expression.isEmpty()) expression = "-(" + expression + ")";
            lastShownExpression = "";
        } else {
            boolean beginsNumber = keyValue.matches("[0-9.]");
            if (justEvaluated) {
                if (beginsNumber || keyValue.endsWith("(") || "pi".equals(keyValue) || "e".equals(keyValue)) expression = "";
                else expression = result;
                justEvaluated = false;
            }
            if (keyValue.endsWith("(") && !expression.isEmpty() && expression.matches(".*[0-9.)]$")) expression += "*";
            expression += keyValue;
            lastShownExpression = "";
        }
        result = expression.isEmpty() ? "0" : expression;
        updateDisplay();
    }

    private void evaluateNow() {
        if (expression.trim().isEmpty()) return;
        try {
            double answer = new ExpressionParser(expression).parse();
            if (!Double.isFinite(answer)) throw new IllegalArgumentException("Result is outside the supported range.");
            lastAnswer = answer;
            lastShownExpression = expression + " =";
            result = fmt(answer);
            history.add(0, lastShownExpression + " " + result);
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
                int eq = item.indexOf(" =");
                String exp = eq > 0 ? item.substring(0, eq) : item;
                expression = exp;
                lastShownExpression = "";
                result = exp;
                justEvaluated = false;
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

    private void showVip() {
        String status = billing != null && billing.isVipActive() ? "VIP is active on this Google Play account."
            : "VIP is currently Free. Purchases work after the monthly and yearly subscription products are set up in Google Play Console.";
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(4));
        TextView info = label(status, 13, text, false);
        layout.addView(info);
        Button monthly = button("Monthly VIP · ₹99", accent, accentText, 14);
        Button yearly = button("Yearly VIP · ₹1,099", accent, accentText, 14);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(10);
        layout.addView(monthly, p); layout.addView(yearly, p);
        monthly.setOnClickListener(v -> {
            if (billing != null) billing.launchSubscription(VipBillingManager.MONTHLY);
            else Toast.makeText(this, "Billing is not ready yet.", Toast.LENGTH_LONG).show();
        });
        yearly.setOnClickListener(v -> {
            if (billing != null) billing.launchSubscription(VipBillingManager.YEARLY);
            else Toast.makeText(this, "Billing is not ready yet.", Toast.LENGTH_LONG).show();
        });
        new AlertDialog.Builder(this).setTitle("DC Calculator VIP").setView(layout)
            .setNegativeButton("Close", null).setNeutralButton("Restore purchases", (d,w) -> {
                if (billing != null) billing.refresh();
            }).show();
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

    @Override public void onBackPressed() {
        if (scientific) { scientific = false; buildUi(); return; }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (billing != null) { billing.close(); billing = null; }
        super.onDestroy();
    }

    /** Safe recursive-descent parser; no JavaScript eval or arbitrary code execution. */
    private static final class ExpressionParser {
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

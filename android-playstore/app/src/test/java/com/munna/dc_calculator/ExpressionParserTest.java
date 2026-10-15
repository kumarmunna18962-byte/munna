package com.munna.dc_calculator;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression tests for the native calculator expression engine. */
public final class ExpressionParserTest {
    private double evaluate(String expression) {
        return new MainActivity.ExpressionParser(expression).parse();
    }

    private void assertClose(double expected, String expression) {
        assertEquals(expression, expected, evaluate(expression), 1e-9);
    }

    @Test public void arithmeticPrecedence() {
        assertClose(15, "12+3");
        assertClose(14, "2+3*4");
        assertClose(2, "(2+4)/3");
        assertClose(0.5, "10/20");
    }

    @Test public void implicitMultiplication() {
        assertClose(14, "2(3+4)");
        assertClose(2 * Math.PI, "2pi");
        assertClose(1, "2sin(30)");
    }

    @Test public void powersAndUnaryMinus() {
        assertClose(8, "2^3");
        assertClose(512, "2^3^2");
        assertClose(-4, "-2^2");
        assertClose(4, "(-2)^2");
    }

    @Test public void functionsAndAngleMode() {
        assertClose(0.5, "sin(30)");
        assertClose(1, "cos(0)");
        assertClose(9, "sqrt(81)");
        assertClose(3, "cbrt(27)");
        assertClose(3, "log(1000)");
        assertClose(Math.log(2), "ln(2)");
        assertClose(Math.E, "exp(1)");
        assertClose(Math.PI, "pi");
        assertClose(Math.E, "e");
    }

    @Test public void postfixOperators() {
        assertClose(120, "5!");
        assertClose(1.5, "150%");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDivisionByZero() {
        evaluate("1/0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidSquareRoot() {
        evaluate("sqrt(-1)");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedExpression() {
        evaluate("2+*3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFactorialOutsideSupportedRange() {
        evaluate("171!");
    }
}

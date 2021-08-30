package mpfun;

import java.math.BigDecimal;

public class MPReal {
    private BigDecimal real;

    public MPReal(int i) {
        this.real = new BigDecimal(i, MPGlobal.mathContext);
    }

    public MPReal(double i) {
        this.real = new BigDecimal(i, MPGlobal.mathContext);
    }

    public MPReal multiply(MPReal s) {
        real = real.multiply(s.real);
        return this;
    }

    public MPReal add(MPReal s) {
        real = real.add(s.real);
        return this;
    }

    public MPReal divide(MPReal s) {
        real = real.divide(s.real);
        return this;
    }

    public MPReal subtract(MPReal s) {
        real = real.subtract(s.real);
        return this;
    }

    public MPReal sqrt() {
        real = real.sqrt(MPGlobal.mathContext);
        return this;
    }

    public double doubleValue() {
        return real.doubleValue();
    }

    public MPReal cos() {
        return new MPReal(Math.cos(real.doubleValue()));
    }

    public MPReal sin() {
        return new MPReal(Math.sin(real.doubleValue()));
    }

    public MPReal negate() {
        real = real.multiply(new BigDecimal(-1));
        return this;
    }
}

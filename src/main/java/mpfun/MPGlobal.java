package mpfun;

import java.math.MathContext;

public class MPGlobal {
    public static MathContext mathContext;

    public static void setMaximumPrecision(MPPrecision mpPrecision) {
        mathContext = new MathContext(mpPrecision.getPrecision());
    }
}

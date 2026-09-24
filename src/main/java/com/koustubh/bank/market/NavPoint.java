package com.koustubh.bank.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One published NAV (price of one unit) of a mutual fund on a day. */
public record NavPoint(LocalDate date, BigDecimal nav) {
}

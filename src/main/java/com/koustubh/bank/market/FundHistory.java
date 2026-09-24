package com.koustubh.bank.market;

import java.util.List;

/** A fund's details and NAV history, newest first, as published by AMFI. */
public record FundHistory(long schemeCode, String schemeName, String fundHouse, String category, List<NavPoint> navs) {
}

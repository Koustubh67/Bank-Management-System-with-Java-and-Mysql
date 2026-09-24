package com.koustubh.bank.market;

/** Where NAV data comes from. The live app uses mfapi.in; tests use fixed data. */
public interface NavSource {

    FundHistory fetch(long schemeCode);
}

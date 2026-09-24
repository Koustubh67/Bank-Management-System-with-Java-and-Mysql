package com.koustubh.bank.web;

import com.koustubh.bank.market.NavPoint;
import org.springframework.stereotype.Component;

import java.util.List;

/** Draws NAV history as SVG polyline points. Used in templates as ${@charts.points(fund.chart, 600, 180)}. */
@Component("charts")
public class Charts {

    public String points(List<NavPoint> navs, int width, int height) {
        if (navs == null || navs.size() < 2) {
            return "";
        }
        double min = navs.stream().mapToDouble(p -> p.nav().doubleValue()).min().orElse(0);
        double max = navs.stream().mapToDouble(p -> p.nav().doubleValue()).max().orElse(1);
        double range = max - min == 0 ? 1 : max - min;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < navs.size(); i++) {
            double x = i * (double) width / (navs.size() - 1);
            double y = height - 4 - (navs.get(i).nav().doubleValue() - min) / range * (height - 8);
            sb.append(String.format("%.1f,%.1f ", x, y));
        }
        return sb.toString().trim();
    }

    /** The same line closed along the bottom edge, for a filled area under the chart. */
    public String area(List<NavPoint> navs, int width, int height) {
        String line = points(navs, width, height);
        return line.isEmpty() ? "" : "0," + height + " " + line + " " + width + "," + height;
    }
}

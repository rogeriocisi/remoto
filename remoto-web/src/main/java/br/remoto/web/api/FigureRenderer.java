package br.remoto.web.api;

import br.remoto.storage.run.ResultFiles;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Exportação de figuras em PNG com JFreeChart 1.5 (a visualização interativa usa uPlot no navegador). */
@Component
class FigureRenderer {

    byte[] png(String title, ResultFiles.Columns columns, boolean spikes) {
        XYSeries series = new XYSeries(title, false, true);
        for (int i = 0; i < columns.x().length; i++) {
            series.add(columns.x()[i], columns.y()[i]);
        }
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = spikes
                ? ChartFactory.createScatterPlot(title, "tempo (ms)", "unidade motora", dataset)
                : ChartFactory.createXYLineChart(title, "tempo (ms)", "valor", dataset);
        chart.removeLegend();
        chart.setBackgroundPaint(Color.WHITE);
        XYItemRenderer renderer = chart.getXYPlot().getRenderer();
        renderer.setSeriesPaint(0, new Color(0x1f, 0x5f, 0x99));
        if (spikes) {
            renderer.setSeriesShape(0, new Rectangle2D.Double(-1, -1, 2, 2));
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ChartUtils.writeChartAsPNG(out, chart, 1200, 600);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

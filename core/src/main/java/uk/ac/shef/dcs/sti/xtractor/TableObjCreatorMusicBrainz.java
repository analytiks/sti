package uk.ac.shef.dcs.sti.xtractor;

import cern.colt.matrix.ObjectMatrix2D;
import org.apache.any23.extractor.html.DomUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.ac.shef.dcs.sti.PlaceHolder;
import uk.ac.shef.dcs.sti.rep.TContentCell;
import uk.ac.shef.dcs.sti.rep.Table;
import uk.ac.shef.dcs.sti.rep.TColumnHeader;
import uk.ac.shef.dcs.sti.rep.LTableContext;
import uk.ac.shef.dcs.util.XPathUtils;

/**
 * Created with IntelliJ IDEA.
 * User: zqz
 * Date: 20/02/14
 * Time: 17:19
 * To change this template use File | Settings | File Templates.
 */
public class TableObjCreatorMusicBrainz implements TableObjCreator {

    @Override
    public Table create(ObjectMatrix2D preTable, String tableId, String sourceId, LTableContext... contexts) {
        Table table = new Table(tableId, sourceId, preTable.rows() - 1, preTable.columns());
        for (LTableContext ctx : contexts)
            table.addContext(ctx);

        //firstly add the header row
        for (int c = 0; c < preTable.columns(); c++) {
            Object o = preTable.get(0, c);
            if (o == null) { //a null value will be inserted by TableHODetector if no user defined header was found
                //todo: header column type
                TColumnHeader header = new TColumnHeader(PlaceHolder.TABLE_HEADER_UNKNOWN.getValue());
                table.setColumnHeader(c, header);

            } else {
                //todo: header column type
                Node e = (Node) o;
                String text = e.getTextContent();
                String xPath = DomUtils.getXPathForNode(e);

                TColumnHeader header = new TColumnHeader(text);
                header.setHeaderXPath(xPath);
                table.setColumnHeader(c, header);
            }
        }

        //then go thru each other rows
        for (int r = 1; r < preTable.rows(); r++) {
            for (int c = 0; c < preTable.columns(); c++) {
                //get url
                Node e = (Node) preTable.get(r, c);
                String text = "";
                String xPath = "";
                String other_text=".";

                NodeList nl = e.getChildNodes();
                for (int i = 0; i < nl.getLength(); i++) {
                    Node an = nl.item(i);
                    if (an.getNodeName().equalsIgnoreCase("A")) {
                        String link = an.getAttributes().getNamedItem("href").getTextContent();
                        other_text+=link;
                        text = an.getTextContent().trim();
                        xPath = DomUtils.getXPathForNode(an);
                        break;
                    }
                    else{
                        text=e.getTextContent().trim();
                        xPath=DomUtils.getXPathForNode(e);
                    }

                }


                TContentCell cell = new TContentCell(text);
                cell.setText(text);
                cell.setOther_text(other_text);

                table.setContentCell(r - 1, c, cell);

                //handle the table row once
                if (c == 0 && xPath != null) {
                    String rowXPath = XPathUtils.trimXPathLastTag("TR", xPath);
                    table.getRowXPaths().put(r, rowXPath);
                }
            }
        }

        if (table.getRowXPaths().size() > 0) {
            String rowXPath = table.getRowXPaths().get(0);
            if (rowXPath == null && table.getRowXPaths().size() > 1)
                rowXPath = table.getRowXPaths().get(1);
            if (rowXPath == null){}
                //System.out.println();
            else {
                String tableXPath = XPathUtils.trimXPathLastTag("TABLE", rowXPath);
                table.setTableXPath(tableXPath);
            }
        }

        return table;
    }
}

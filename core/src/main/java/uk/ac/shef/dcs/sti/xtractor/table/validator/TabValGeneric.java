package uk.ac.shef.dcs.sti.xtractor.table.validator;

import uk.ac.shef.dcs.sti.core.model.TCell;
import uk.ac.shef.dcs.sti.core.model.Table;
import uk.ac.shef.dcs.sti.xtractor.ContentValidator;

/**
 * Author: Ziqi Zhang (z.zhang@dcs.shef.ac.uk)
 * Date: 26/10/12
 * Time: 10:47
 * implements following policies:
 * - Must have no more than 1 empty columns (a column is empty if over 20% of cells are empty)
 * - Must have no more than 1 lengthy columns (a column is lengthy if it has more than 10% cells lengthy(more than 5 words, or a multi-valued item))
 * - Must have no more than 1 link columns
 * - Must have at least 2 columns and over 50% of columns satisfying all above conditions (excl. numeric columns)
 * - Must have at least 3 data rows
 */
public class TabValGeneric extends ContentValidator implements TableValidator {
    protected static final double THRESHOLD_MAX_ALLOWED_EMPTY_CELLS_IN_COLUMN = 0.2;
    protected static final double THRESHOLD_MAX_ALLOWED_LENGTHY_CELLS_IN_COLUMN = 0.1;
    protected static final double THRESHOLD_MAX_ALLOWED_NUMERIC_CELLS_IN_COLUMN = 0.1;

    protected static final int THRESHOLD_MIN_PROPER_DATA_COLUMNS = 2;
    protected static final double THRESHOLD_MIN_PROPER_DATA_COLUMNS_FRAC = 0.5;
    protected static final int THRESHOLD_MIN_PROPER_DATA_ROWS = 3;

    protected static final int THRESHOLD_MAX_COLUMNS_WITH_LINKS = 1;
    protected static final double THRESHOLD_MAX_ALLOWED_LINK_CELLS_IN_COLUMN = 0.1;

    protected static int THRESHOLD_LENGTHY_CELL_MAXMULTIVALUEITEM = 5; //max # of valueitems allowed in a multi-value cell
    protected static int THRESHOLD_LENGTHY_CELL_MAXSINGLEVALUELENGTH = 5; //max # of tokens in a single cell VALUE (ie.
    // if there are multi. links/lists in a cell, this is for each of them AND the entire text cell length cannot be
    // longer than [# of links] * this value

    public TabValGeneric() {
    }


    @Override
    public boolean validate(Table table) {
        int countEmptyColumns = 0, countLengthyColumns = 0, countNumericColumns = 0, countLinkColumns = 0;

        if (table.getNumRows() < THRESHOLD_MIN_PROPER_DATA_ROWS)
            return false;

        for (int c = 0; c < table.getNumCols(); c++) {
            int countLengthyPerCol = 0, countNumericPerCol = 0, countEmptyPerCol = 0, countLinksPerCol = 0;

            for (int r = 0; r < table.getNumRows(); r++) {
                TCell ltc = (TCell) table.getContentCell(r, c);
                String tcText = ltc.getText();

                if (isLinkCell(ltc)) {
                    countLinksPerCol++;
                }
                if (isEmptyString(tcText)) {
                    countEmptyPerCol++;
                }
                if (isLengthyCell(ltc)) {
                    countLengthyPerCol++;
                }
                if (isNumericContent(tcText)) {
                    countNumericPerCol++;
                }
            }

        }

        return true;
    }

    @Deprecated
    public static boolean isLengthyCell(TCell tc) {
       /* if (tc.getValuesAndURIs().size() > 1)
            return true;

        for (Map.Entry<String, String> e : tc.getValuesAndURIs().entrySet()) {
            if (e.getKey().split("\\s+").length > THRESHOLD_LENGTHY_CELL_MAXSINGLEVALUELENGTH)
                return true;
        }*/

        int textLength = tc.getText().split("\\s+").length;
        return textLength > TabValWikipediaGSLanient.THRESHOLD_LENGTHY_CELL_MAXSINGLEVALUELENGTH;
    }

    @Deprecated
    public static boolean isLinkCell(TCell tc) {
        /*return tc.getValuesAndURIs().size() > 0;*/
        return false;
    }

    public static boolean tooManyLengthyCellsInColumn(TCell[] cells) {
        int countLengthyPerCol = 0;
        for (TCell ltc : cells) {
            if (isLengthyCell(ltc)) {
                countLengthyPerCol++;
            }
        }
        return countLengthyPerCol > cells.length * THRESHOLD_MAX_ALLOWED_LENGTHY_CELLS_IN_COLUMN;
    }

    public static boolean hasLengthyCellsInColumn(TCell[] cells) {
        for (TCell ltc : cells) {
            if (isLengthyCell(ltc)) {
                return true;
            }
        }
        return false;
    }

    public static boolean tooManyEmptyCellsInColumn(TCell[] cells) {
        int countEmptyPerCol = 0;
        for (TCell ltc : cells) {
            if (isEmptyString(ltc.getText())) {
                countEmptyPerCol++;
            }
        }
        return countEmptyPerCol > cells.length * THRESHOLD_MAX_ALLOWED_EMPTY_CELLS_IN_COLUMN;
    }

    public static boolean hasEmptyCellsInColumn(TCell[] cells) {

        for (TCell ltc : cells) {
            if (isEmptyString(ltc.getText())) {
                return true;
            }
        }
        return false;
    }

    public static boolean tooManyNumericCellsInColumn(TCell[] cells) {
        int countNumericPerCol = 0;
        for (TCell ltc : cells) {
            if (isNumericContent(ltc.getText())) {
                countNumericPerCol++;
            }
        }
        return countNumericPerCol > cells.length * THRESHOLD_MAX_ALLOWED_NUMERIC_CELLS_IN_COLUMN;
    }

    public static boolean hasNumericCellsInColumn(TCell[] cells) {
        for (TCell ltc : cells) {
            if (isNumericContent(ltc.getText())) {
                return true;
            }
        }
        return false;
    }

    public static boolean tooManyLinkCellsInColumn(TCell[] cells) {
        int countLinkCellPerCol = 0;
        for (TCell ltc : cells) {
            if (isLinkCell(ltc)) {
                countLinkCellPerCol++;
            }
        }
        return countLinkCellPerCol > cells.length * THRESHOLD_MAX_ALLOWED_LINK_CELLS_IN_COLUMN;
    }

    public static boolean hasLinkCellsInColumn(TCell[] cells) {
        for (TCell ltc : cells) {
            if (isLinkCell(ltc)) {
                return true;
            }
        }
        return false;
    }
}
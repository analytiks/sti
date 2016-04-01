/*
package uk.ac.shef.dcs.sti.algorithm.tm.maincol;

import cern.colt.matrix.DoubleMatrix2D;
import javafx.util.Pair;
import org.apache.solr.client.solrj.SolrServer;
import uk.ac.shef.dcs.sti.algorithm.tm.sampler.TContentRowRanker;
import uk.ac.shef.dcs.sti.misc.DataTypeClassifier;
import uk.ac.shef.dcs.sti.algorithm.tm.stopping.StoppingCriteriaInstantiator;
import uk.ac.shef.dcs.sti.rep.Table;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

*/
/**
 * This class implements a decision tree logic to infer among all columns in a table, which ONE is likely the main entity
 * column
 *//*

public class MainColumnFinder_old {

    private static Logger LOG = Logger.getLogger(MainColumnFinder_old.class.getName());
    private TColumnFeatureGenerator featureGenerator;
    private TContentRowRanker row_sampler;
    private String stoppingCriteriaClassname;
    private String[] stoppingCriteriaParams;

    public MainColumnFinder_old(String cache, String nlpResource, List<String> stopwords,String... searchAPIKeys) throws IOException {
        featureGenerator = new TColumnFeatureGenerator(cache, nlpResource, stopwords, searchAPIKeys);
    }

    public MainColumnFinder_old(TContentRowRanker sampler, String stoppingCriteriaClassname, String[] stoppingCriteriaParams,
                            String cache, String nlpResource, List<String> stopwords,String... searchAPIKeys) throws IOException {
        featureGenerator = new TColumnFeatureGenerator(cache, nlpResource,stopwords, searchAPIKeys);
        this.row_sampler = sampler;
        this.stoppingCriteriaClassname = stoppingCriteriaClassname;
        this.stoppingCriteriaParams = stoppingCriteriaParams;
    }

    public MainColumnFinder_old(TContentRowRanker sampler, String stoppingCriteriaClassname, String[] stoppingCriteriaParams,
                            SolrServer cache, String nlpResource, List<String> stopwords,String... searchAPIKeys) throws IOException {
        featureGenerator = new TColumnFeatureGenerator(cache, nlpResource,stopwords, searchAPIKeys);
        this.row_sampler = sampler;
        this.stoppingCriteriaClassname = stoppingCriteriaClassname;
        this.stoppingCriteriaParams = stoppingCriteriaParams;
    }

    */
/**
     * The decision tree logic is:
     * 1. If col is the only NE likely col in the table, choose the column
     * 2. If col is NE likely, and it is the only one having non-empty cells, choose the column
     *
     * @param table
     * @return a list of ObjectWithObject objects, where first object is the column index; second is the computeElementScores
     *         probability that asserts that column being the main column of the table. (only NE likely columns can be
     *         considered main column)
     *//*

    public List<Pair<Integer, Pair<Double, Boolean>>> compute(Table table, int... skipColumns) {
        List<Pair<Integer, Pair<Double, Boolean>>> rs = new ArrayList<>();

        //1. initiate all columns' feature objects
        List<TColumnFeature> allColumnCandidates = new ArrayList<TColumnFeature>(table.getNumCols());
        for (int c = 0; c < table.getNumCols(); c++){
            boolean skip=false;
            for(int i: skipColumns) {
                if(c==i) {
                    skip=true;
                    break;
                }
            }
            if(!skip)
                allColumnCandidates.add(new TColumnFeature(c, table.getNumRows()));
        }

        //2. infer column datatype
        featureGenerator.setColumnDataTypes(table);

        //3. infer the most frequent datatype,
        featureGenerator.setMostFrequentDataTypes(allColumnCandidates, table);

        //4. select only NE columns to further process
        List<TColumnFeature> allNEColumnCandidates = new ArrayList<TColumnFeature>();
        for (TColumnFeature cf : allColumnCandidates) {
            if (cf.getMostFrequentDataType().getType().equals(DataTypeClassifier.DataType.NAMED_ENTITY))
                allNEColumnCandidates.add(cf);
        }
        //EXCEPTION: what if no NE columns found?
        for (TColumnFeature cf : allColumnCandidates) {
            if (cf.getMostFrequentDataType().getType().equals(DataTypeClassifier.DataType.SHORT_TEXT))
                allNEColumnCandidates.add(cf);
        }

        featureGenerator.setUniqueValueCount(allNEColumnCandidates, table);
        featureGenerator.setAcronymColumnBoolean(allNEColumnCandidates, table);
        featureGenerator.setEmptyCellCount(allNEColumnCandidates, table);

        //EXCEPTION: what if no SHORT TEXT columns found?
        if (allNEColumnCandidates.size() == 0) {
            LOG.warning("This table does not contain columns that are likely to contain named entities.");
            Pair<Integer, Pair<Double, Boolean>> oo = new Pair<>(0,new Pair<>(1.0, false));

            rs.add(oo);
            for (TColumnFeature cf : allColumnCandidates) {
                table.getColumnHeader(cf.getColId()).setFeature(cf);
            }
            return rs;
        }

        //5. is any NE column the only valid NE column in the table?
        int onlyNECol = featureGenerator.setOnlyNEColumn(allNEColumnCandidates);
        //5 - yes:
        if (onlyNECol != -1) {
            Pair<Integer, Pair<Double, Boolean>> oo = new Pair<>(onlyNECol,new Pair<>(1.0, false));
            rs.add(oo);
            for (TColumnFeature cf : allColumnCandidates) {
                table.getColumnHeader(cf.getColId()).setFeature(cf);
            }
            return rs;
        }

        //6. is any NE column the only one that has no empty cells?
        int onlyNECol_with_no_emtpy = -1, num = 0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.getEmptyCellCount() == 0) {
                num++;
                if (onlyNECol_with_no_emtpy == -1)
                    onlyNECol_with_no_emtpy = cf.getColId();
                else
                    break;
            }
        }
        //6 - yes:
        if (onlyNECol_with_no_emtpy != -1 && num == 1) {
            if (!allColumnCandidates.get(onlyNECol_with_no_emtpy).isAcronymColumn()) {
                Pair<Integer, Pair<Double, Boolean>> oo = new Pair<>(onlyNECol_with_no_emtpy,
                        new Pair<>(1.0, false));
                rs.add(oo);
                for (TColumnFeature cf : allColumnCandidates) {
                    table.getColumnHeader(cf.getColId()).setFeature(cf);
                }
                return rs;
            }
        }

        //7. is any NE column the only one that has non-duplicate values on every row?
        int onlyNECol_non_duplicate = -1;
        num = 0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.getUniqueCellCount() == 1.0) {
                num++;
                if (onlyNECol_non_duplicate == -1)
                    onlyNECol_non_duplicate = cf.getColId();
                else
                    break;
            }
        }

        //7 - yes:
        if (onlyNECol_non_duplicate != -1 && num == 1) {
            if (!allColumnCandidates.get(onlyNECol_non_duplicate).isAcronymColumn()) {
                Pair<Integer, Pair<Double, Boolean>> oo = new Pair<>(onlyNECol_non_duplicate,
                        new Pair<>(1.0, false));
                rs.add(oo);
                for (TColumnFeature cf : allColumnCandidates) {
                    table.getColumnHeader(cf.getColId()).setFeature(cf);
                }
                return rs;
            }
        }

        //7.5 ====== this is a dangerous rule as it MAY overdo (have not checked thou) true positives ======
        List<Integer> ignoreColumns = new ArrayList<Integer>();
        featureGenerator.setInvalidHeaderTextSyntax(allNEColumnCandidates, table);
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.isInvalidPOS())
                ignoreColumns.add(cf.getColId());
        }
        //if columns to be ignored due to invalid header text is less than total columns to be considered,we can isValidAttribute them
        //otherwise, if we are told all columns should be ignored, dont isValidAttribute any candidate ne columns
        if (ignoreColumns.size() != allNEColumnCandidates.size()) {
            Iterator<TColumnFeature> it = allNEColumnCandidates.iterator();
            while (it.hasNext()) {
                TColumnFeature cf = it.next();
                if (cf.isInvalidPOS())
                    it.remove();
            }
        }
        if (allNEColumnCandidates.size() == 1) {
            Pair<Integer, Pair<Double, Boolean>> oo = new Pair<>(allNEColumnCandidates.get(0).getColId(),
                    new Pair<>(1.0, false));
            rs.add(oo);
            for (TColumnFeature cf : allColumnCandidates) {
                table.getColumnHeader(cf.getColId()).setFeature(cf);
            }
            return rs;
        }

        //8. generate feature - 1st NE column
        featureGenerator.setIsFirstNEColumn(allNEColumnCandidates);

        //9. generate features - context computeElementScores
        LOG.finest("Computing context matching");
        featureGenerator.setCMScores(allNEColumnCandidates, table);

        //10. generate features - web search matcher
        LOG.finest("Computing web search matching (total rows " + table.getNumRows());
        try {
            DoubleMatrix2D scores;
            if (row_sampler != null) {
                scores = featureGenerator.setWSScores(allNEColumnCandidates, table,
                        row_sampler, StoppingCriteriaInstantiator.instantiate(stoppingCriteriaClassname, stoppingCriteriaParams), 1);
            } else {
                scores = featureGenerator.setWSScores(allNEColumnCandidates, table);
            }
            double total = 0.0;
            for (TColumnFeature cf : allNEColumnCandidates) {
                for (int row = 0; row < scores.rows(); row++) {
                    total += scores.get(row, cf.getColId());
                }
                cf.setWebSearchScore(total);
                total = 0.0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        //12. then let's perform reasoning based on the remaining features: diversity computeElementScores; 1st ne column; context computeElementScores; web search computeElementScores
        final Map<Integer, Pair<Double, Boolean>> inferenceScores = infer_multiFeatures(allNEColumnCandidates);
        List<Integer> candidates = new ArrayList<Integer>(inferenceScores.keySet());
        final Map<Integer, TColumnFeature> map_column_to_columnFeature = new HashMap<Integer, TColumnFeature>();
        for (TColumnFeature cf : allNEColumnCandidates) {
            map_column_to_columnFeature.put(cf.getColId(), cf);
        }
        Collections.sort(candidates, new Comparator<Integer>() { //sort by computeElementScores first; then column, left most first
            @Override
            public int compare(Integer o1, Integer o2) {
                int compared = inferenceScores.get(o2).getKey().compareTo(inferenceScores.get(o1).getKey());
                if (compared == 0) { //where there is a tie, choose the one having the highest diversity computeElementScores
                    Double vd_o1 = map_column_to_columnFeature.get(o1).getUniqueCellCount();
                    Double vd_o2 = map_column_to_columnFeature.get(o2).getUniqueCellCount();
                    compared = vd_o2.compareTo(vd_o1);
                    if (compared == 0) {
                        vd_o1 = map_column_to_columnFeature.get(o1).getUniqueTokenCount();
                        vd_o2 = map_column_to_columnFeature.get(o2).getUniqueTokenCount();
                        return vd_o2.compareTo(vd_o1);
                    }
                }
                return compared;
            }
        });

        for (int ci : candidates) {
            Pair<Integer, Pair<Double, Boolean>> oo = new Pair<>(ci,inferenceScores.get(ci));
            rs.add(oo);
        }

        for (TColumnFeature cf : allColumnCandidates) {
            table.getColumnHeader(cf.getColId()).setFeature(cf);
        }
        return rs;
    }

    //key: col id; value: computeElementScores
    //currently performs following scoring: diversity; context computeElementScores; 1st ne column; acronym column checker; search
    //results are collected as number of votes by each dimension
    private Map<Integer, Pair<Double, Boolean>> infer_multiFeatures(List<TColumnFeature> allNEColumnCandidates) {
        Map<Integer, Pair<Double, Boolean>> votes = new HashMap<>();
        //a. vote by diversity computeElementScores
        Collections.sort(allNEColumnCandidates, new Comparator<TColumnFeature>() {
            @Override
            public int compare(TColumnFeature o1, TColumnFeature o2) {
                int compared = new Double(o2.getUniqueCellCount()).compareTo(o1.getUniqueCellCount());
                if (compared == 0)
                    return new Double(o2.getUniqueTokenCount()).compareTo(o1.getUniqueTokenCount());
                return compared;
            }
        });
        double maxDiversityScore = -1.0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            double diversity = cf.getUniqueTokenCount() + cf.getUniqueCellCount();
            if (diversity >= maxDiversityScore && diversity != 0) {
                maxDiversityScore = diversity;
                votes.put(cf.getColId(), new Pair<>(1.0, false));
            } else
                break; //already sorted, so following this there shouldnt be higher diversity scores
        }


        //b. vote by 1st ne column
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.isFirstNEColumn()) {
                Pair<Double, Boolean> entry = votes.get(cf.getColId());
                entry = entry == null ? new Pair<>(0.0, false) : entry;
                Double vts = entry.getKey();
                vts = vts + 1.0;
                entry = new Pair<>(vts, entry.getValue());
                votes.put(cf.getColId(), entry);
                break;
            }
        }
        //c. vote by context matcher
        Collections.sort(allNEColumnCandidates, new Comparator<TColumnFeature>() {
            @Override
            public int compare(TColumnFeature o1, TColumnFeature o2) {
                return new Double(o2.getCMScore()).compareTo(o1.getCMScore());
            }
        });
        double maxContextMatchScore = -1.0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.getCMScore() >= maxContextMatchScore && cf.getCMScore() != 0) {
                maxContextMatchScore = cf.getCMScore();
                Pair<Double, Boolean> entry = votes.get(cf.getColId());
                entry = entry == null ? new Pair<>(0.0, false) : entry;
                Double vts = entry.getKey();
                vts = vts + 1.0;
                entry = new Pair<>(vts, entry.getValue());
                votes.put(cf.getColId(), entry);
            } else
                break;
        }
        //d. vote by acronym columns
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.isAcronymColumn()) {
                Pair<Double, Boolean> entry = votes.get(cf.getColId());
                entry = entry == null ? new Pair<>(0.0, false) : entry;
                entry = new Pair<>(entry.getKey(), true);
                votes.put(cf.getColId(), entry);
            }
        }

        //e. vote by search matcher
        Collections.sort(allNEColumnCandidates, new Comparator<TColumnFeature>() {
            @Override
            public int compare(TColumnFeature o1, TColumnFeature o2) {
                return new Double(o2.getWSScore()).compareTo(o1.getWSScore());
            }
        });
        double maxSearchMatchScore = -1.0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.getWSScore() >= maxSearchMatchScore && cf.getWSScore() != 0) {
                maxSearchMatchScore = cf.getWSScore();
                Pair<Double, Boolean> entry = votes.get(cf.getColId());
                entry = entry == null ? new Pair<>(0.0, false) : entry;
                Double vts = entry.getKey();
                vts = vts + 1.0;
                entry = new Pair<>(vts, entry.getValue());
                votes.put(cf.getColId(), entry);
            } else
                break;
        }

        for (TColumnFeature cf : allNEColumnCandidates) {
            if (votes.containsKey(cf.getColId()))
                continue;
            votes.put(cf.getColId(), new Pair<>(0.0, false));
        }
        return votes;
    }


    //key: col id; value: computeElementScores
    //currently performs following scoring: diversity; context computeElementScores; 1st ne column; NO search
    //results are collected as number of votes by each dimension
    private Map<Integer, Double> infer_multiFeatures_without_search(List<TColumnFeature> allNEColumnCandidates) {
        Map<Integer, Double> votes = new HashMap<Integer, Double>();
        //a. vote by diversity computeElementScores
        Collections.sort(allNEColumnCandidates, new Comparator<TColumnFeature>() {
            @Override
            public int compare(TColumnFeature o1, TColumnFeature o2) {
                int compared = new Double(o2.getUniqueCellCount()).compareTo(o1.getUniqueCellCount());
                if (compared == 0)
                    return new Double(o2.getUniqueTokenCount()).compareTo(o1.getUniqueTokenCount());
                return compared;
            }
        });
        double maxDiversityScore = -1.0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (maxDiversityScore == -1.0) {
                maxDiversityScore = cf.getUniqueCellCount() + cf.getUniqueTokenCount();
                votes.put(cf.getColId(), 1.0);
            } else if ((cf.getUniqueCellCount() + cf.getUniqueTokenCount()) < maxDiversityScore)
                break;
            else
                votes.put(cf.getColId(), 1.0);
        }
        //b. vote by 1st ne column
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (cf.isFirstNEColumn()) {
                Double vts = votes.get(cf.getColId());
                vts = vts == null ? 0 : vts;
                vts = vts + 1.0;
                votes.put(cf.getColId(), vts);
            }
        }
        //c. vote by context matcher
        Collections.sort(allNEColumnCandidates, new Comparator<TColumnFeature>() {
            @Override
            public int compare(TColumnFeature o1, TColumnFeature o2) {
                return new Double(o2.getCMScore()).compareTo(o1.getCMScore());
            }
        });
        double maxContextMatchScore = -1.0;
        for (TColumnFeature cf : allNEColumnCandidates) {
            if (maxContextMatchScore == -1.0) {
                maxContextMatchScore = cf.getCMScore();
                Double vts = votes.get(cf.getColId());
                vts = vts == null ? 0 : vts;
                vts = vts + 1;
                votes.put(cf.getColId(), vts);
            } else if (cf.getCMScore() < maxContextMatchScore)
                break;
            else {
                Double vts = votes.get(cf.getColId());
                vts = vts == null ? 0 : vts;
                vts = vts + 1;
                votes.put(cf.getColId(), vts);
            }
        }
        return votes;
    }

}
*/

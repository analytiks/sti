package uk.ac.shef.dcs.sti.algorithm.tm;

import javafx.util.Pair;
import uk.ac.shef.dcs.kbsearch.KBSearch;
import uk.ac.shef.dcs.sti.STIException;
import uk.ac.shef.dcs.sti.nlp.NLPTools;
import uk.ac.shef.dcs.sti.misc.DataTypeClassifier;
import uk.ac.shef.dcs.sti.algorithm.tm.sampler.TContentCellRanker;
import uk.ac.shef.dcs.sti.experiment.TableMinerConstants;
import uk.ac.shef.dcs.kbsearch.rep.Clazz;
import uk.ac.shef.dcs.kbsearch.rep.Entity;
import uk.ac.shef.dcs.kbsearch.rep.Resource;
import uk.ac.shef.dcs.sti.rep.*;
import uk.ac.shef.dcs.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**

 */
public class UPDATE {

    private TCellDisambiguator disambiguator;
    private KBSearch kbSearch;
    private TColumnClassifier classification_scorer;
    private String nlpTools_folder;
    private TContentCellRanker selector;
    private List<String> stopWords;

    public UPDATE(TContentCellRanker selector,
                  KBSearch kbSearch,
                  TCellDisambiguator disambiguator,
                  TColumnClassifier classification_scorer,
                  List<String> stopwords,
                  String nlpTools_folder) {
        this.selector = selector;
        this.kbSearch = kbSearch;
        this.disambiguator = disambiguator;
        this.classification_scorer = classification_scorer;
        this.nlpTools_folder = nlpTools_folder;
        this.stopWords = stopwords;
    }

    public void update(
            List<Integer> interpreted_columns,
            Table table,
            TAnnotation current_iteration_annotation
    ) throws IOException, STIException {

        int current_iteration = 0;
        TAnnotation prev_iteration_annotation = new TAnnotation(current_iteration_annotation.getRows(), current_iteration_annotation.getCols());

                TAnnotation.copy(current_iteration_annotation, prev_iteration_annotation);
        List<String> domain_representation;
        Set<String> processed_entity_ids = new HashSet<String>();
        boolean converged;
        do {
            ////// solution 1: both prev and current iterations' headers do not have dc added
            /*System.out.println("\t>> UPDATE begins, iteration:" + current_iteration);
            //current iteration annotation header scores does not contain dc scores

            //headers will have dc score added
            domain_representation = construct_domain_represtation(table, current_iteration_annotation, interpreted_columns);
            revise_header_annotation(current_iteration_annotation, domain_representation, interpreted_columns);

            //scores will be reset, then recalculated. dc scores lost
            revise_cell_disambiguation_then_reannotate_cell_and_header(table, current_iteration_annotation, interpreted_columns);

            //both prev and current iterations' header annotations do not have dc scores
            converged = checkConvergence(prev_iteration_annotation, current_iteration_annotation,
                    table.getNumRows(), interpreted_columns);
            if (!converged)
                prev_iteration_annotation = TAnnotation.copy(current_iteration_annotation,
                        table.getNumRows(), table.getNumCols());
            current_iteration++;*/

            ///////////////// solution 2: both prev and current iterations' headers will have dc scores added
            System.out.println("\t>> UPDATE begins, iteration:" + current_iteration);
            processed_entity_ids.addAll(initialize_processed_entity_ids(table, current_iteration_annotation));
            //current iteration annotation header scores does not contain dc scores

            //headers will have dc score added
            domain_representation = construct_domain_represtation(table, current_iteration_annotation, interpreted_columns);
            revise_header_annotation(current_iteration_annotation, domain_representation, interpreted_columns);
            //add dc scores to prev iteration's header annotations
            prev_iteration_annotation = new TAnnotation(current_iteration_annotation.getRows(),
                    current_iteration_annotation.getCols());
            TAnnotation.copy(current_iteration_annotation, prev_iteration_annotation);

            //scores will be reset, then recalculated. dc scores lost
            revise_cell_disambiguation_then_reannotate_cell_and_header(processed_entity_ids,
                    table, current_iteration_annotation, interpreted_columns);
            //add dc scores to the current, new iteration's header annotations


            // NO NEED!!! DC score already included when "revise_cell_disam..."
            //revise_header_annotation(current_iteration_annotation, domain_representation, interpreted_columns);
            //both prev and current iterations' header annotations do not have dc scores
            converged = checkConvergence(prev_iteration_annotation, current_iteration_annotation,
                    table.getNumRows(), interpreted_columns);
            if (!converged) {
                prev_iteration_annotation = new TAnnotation(current_iteration_annotation.getRows(),
                        current_iteration_annotation.getCols());
                TAnnotation.copy(current_iteration_annotation,
                        prev_iteration_annotation);
            }
            current_iteration++;
        } while (!converged && current_iteration < TableMinerConstants.UPDATE_PHASE_MAX_ITERATIONS);

        if (current_iteration >= TableMinerConstants.UPDATE_PHASE_MAX_ITERATIONS) {
            System.out.println("\t>> UPDATE CANNOT STABLIZE AFTER " + current_iteration + " ITERATIONS, Stopped");
            if (prev_iteration_annotation != null) {
                current_iteration_annotation = new TAnnotation(prev_iteration_annotation.getRows(),
                        prev_iteration_annotation.getCols());
                TAnnotation.copy(prev_iteration_annotation,
                        current_iteration_annotation);
            }
        } else
            System.out.println("\t>> UPDATE STABLIZED AFTER " + current_iteration + " ITERATIONS");

    }

    private Set<String> initialize_processed_entity_ids(Table table, TAnnotation prev_iteration_annotation) {
        Set<String> ids = new HashSet<String>();
        for (int col = 0; col < table.getNumCols(); col++) {
            for (int row = 0; row < table.getNumRows(); row++) {
                TCellAnnotation[] cas = prev_iteration_annotation.getContentCellAnnotations(row, col);
                if (cas == null)
                    continue;
                for (TCellAnnotation ca : cas) {
                    ids.add(ca.getAnnotation().getId());
                }
            }
        }
        return ids;
    }

    private void revise_cell_disambiguation_then_reannotate_cell_and_header(
            Set<String> already_built_feature_space_entity_candidates,
            Table table, TAnnotation current_iteration_annotation, List<Integer> interpreted_columns) throws IOException {
        for (int c : interpreted_columns) {
            List<List<Integer>> ranking = selector.select(table, c, current_iteration_annotation.getSubjectColumn());

            List<HeaderAnnotation> bestHeaderAnnotations = current_iteration_annotation.getBestHeaderAnnotations(c);
            Set<String> columnTypes = new HashSet<String>();
            for (HeaderAnnotation ha : bestHeaderAnnotations)
                columnTypes.add(ha.getAnnotation_url());
            List<Integer> updated = new ArrayList<Integer>();
            for (int bi = 0; bi < ranking.size(); bi++) {
                List<Integer> rows = ranking.get(bi);
                TContentCell sample = table.getContentCell(rows.get(0), c);
                if (sample.getType().equals(DataTypeClassifier.DataType.LONG_TEXT)) {
                    System.out.println("\t\t>>> Long text cell skipped: " + rows + "," + c + " " + sample.getText());
                    continue;
                }
                if (sample.getText().length() < 2) {
                    System.out.println("\t\t>>> Long text cell skipped: " + rows + "," + c + " " + sample.getText());
                    continue;
                }

                List<Pair<Entity, Map<String, Double>>>
                        candidates_and_scores_for_block =
                        disambiguate(already_built_feature_space_entity_candidates,
                                sample,
                                table,
                                //current_iteration_annotation,
                                columnTypes,
                                rows, c);

                if (candidates_and_scores_for_block.size() > 0) {
                    update_entity_annotations(table, current_iteration_annotation, rows, c,
                            candidates_and_scores_for_block);
                    updated.addAll(rows);
                }
            }
            if (TableMinerConstants.ENFORCE_ONPSPD)
                ONPSPD_Enforcer.enforce(table, current_iteration_annotation, c);

            System.out.println("\t>> Classification-UPDATE (update " + updated.size() + " rows)");
            if (TableMinerConstants.CLASSIFICATION_CANDIDATE_CONTRIBUTION_METHOD == 0)
                update_typing_annotations_best_candidate_contribute(updated, c, current_iteration_annotation, table, table.getNumRows());
            else
                update_typing_annotations_all_candidate_contribute(updated, c, current_iteration_annotation, table, table.getNumRows());

        }

    }

    private void revise_header_annotation(TAnnotation current_iteration_annotation, List<String> domain_representation,
                                          List<Integer> interpreted_columns) {
        for (int c : interpreted_columns) {
            List<HeaderAnnotation> headers = new ArrayList<HeaderAnnotation>(
                    Arrays.asList(current_iteration_annotation.getHeaderAnnotation(c)));

            for (HeaderAnnotation ha : headers) {
                double domain_consensus = classification_scorer.score_domain_consensus(ha, domain_representation);
                ha.setFinalScore(ha.getFinalScore() + domain_consensus);
            }

            Collections.sort(headers);
            current_iteration_annotation.setHeaderAnnotation(c, headers.toArray(new HeaderAnnotation[0]));
        }
    }

    public List<String> construct_domain_represtation(Table table, TAnnotation current_iteration_annotation, List<Integer> interpreted_columns) {
        List<String> domain = new ArrayList<String>();
        for (int c : interpreted_columns) {
            for (int r = 0; r < table.getNumRows(); r++) {
                TCellAnnotation[] annotations = current_iteration_annotation.getContentCellAnnotations(r, c);
                if (annotations != null && annotations.length > 0) {
                    Entity ec = annotations[0].getAnnotation();
                    try {
                        domain.addAll(build_domain_rep_for_entity(ec));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return domain;
    }

    private Collection<? extends String> build_domain_rep_for_entity(Entity ec) throws IOException {
        List<String> domain = new ArrayList<String>();
        for (String[] fact : ec.getTriples()) {
            if (fact[0].equals("/common/topic/description")) {
                String[] sentences = NLPTools.getInstance(nlpTools_folder).getSentenceSplitter().sentDetect(fact[1]);
                String first = sentences.length > 0 ? sentences[0] : "";
                List<String> tokens = StringUtils.splitToAlphaNumericTokens(first, true);
                Iterator<String> it = tokens.iterator();
                while (it.hasNext()) {
                    String tok = it.next();
                    if (tok.trim().length() < 2 || stopWords.contains(tok))
                        it.remove();
                }
                domain.addAll(tokens);
            }
        }
        return domain;
    }

    private boolean checkConvergence(TAnnotation prev_iteration_annotation, TAnnotation table_annotation, int totalRows, List<Integer> interpreted_columns) {
        //check header annotations
        int header_converged_count = 0;
        boolean header_converged = false;
        for (int c : interpreted_columns) {
            List<HeaderAnnotation> header_annotations_prev_iteration = prev_iteration_annotation.getBestHeaderAnnotations(c);
            List<HeaderAnnotation> header_annotations_current_iteration = table_annotation.getBestHeaderAnnotations(c);
            if (header_annotations_current_iteration.size() == header_annotations_prev_iteration.size()) {
                header_annotations_current_iteration.retainAll(header_annotations_prev_iteration);
                if (header_annotations_current_iteration.size() == header_annotations_prev_iteration.size())
                    header_converged_count++;
                else
                    return false;
            } else
                return false;
        }
        if (header_converged_count == interpreted_columns.size()) {
            header_converged = true;
        }

        //check cell annotations
        boolean cell_converged = true;
        for (int c : interpreted_columns) {
            for (int row = 0; row < totalRows; row++) {
                List<TCellAnnotation> cell_prev_annotations = prev_iteration_annotation.getBestContentCellAnnotations(row, c);
                List<TCellAnnotation> cell_current_annotations = table_annotation.getBestContentCellAnnotations(row, c);
                if (cell_current_annotations.size() == cell_prev_annotations.size()) {
                    cell_current_annotations.retainAll(cell_prev_annotations);
                    if (cell_current_annotations.size() != cell_prev_annotations.size())
                        return false;
                }
            }
        }
        return header_converged && cell_converged;
    }

    public void update_typing_annotations_best_candidate_contribute(List<Integer> rowsUpdated,
                                                                    int column,
                                                                    TAnnotation table_annotations,
                                                                    Table table,
                                                                    int tableRowsTotal) {
        HeaderAnnotation[] existing_header_annotations = table_annotations.getHeaderAnnotation(column);
        reset_entity_contributed_scores(existing_header_annotations);
        existing_header_annotations = existing_header_annotations == null ? new HeaderAnnotation[0] : existing_header_annotations;

        //supporting rows are only added if a header for the type of the cell annotation exists
        Set<HeaderAnnotation> add = new HashSet<HeaderAnnotation>();
        //any new headers due to disambiguation-update?
        for (int row : rowsUpdated) {
            List<TCellAnnotation> bestCellAnnotations = table_annotations.getBestContentCellAnnotations(row, column);
            for (TCellAnnotation ca : bestCellAnnotations) {
                HeaderAnnotationUpdater.add(ca, column, table, existing_header_annotations, add);
            }
        }
        //add or not?
        if (TableMinerConstants.ALLOW_NEW_HEADERS_AT_DISAMBIGUATION_UPDATE) {
            for (HeaderAnnotation eh : existing_header_annotations)
                add.add(eh);
            existing_header_annotations = add.toArray(new HeaderAnnotation[0]);
        }

        existing_header_annotations = HeaderAnnotationUpdater.update_best_entity_contribute(       //this time dc score already included
                rowsUpdated.toArray(new Integer[0]),
                column,
                tableRowsTotal,
                existing_header_annotations,
                table,
                table_annotations,
                classification_scorer
        );
        table_annotations.setHeaderAnnotation(column, existing_header_annotations);

    }

    private void reset_entity_contributed_scores(HeaderAnnotation[] existing_header_annotations) {
        for (HeaderAnnotation ha : existing_header_annotations) {
            ha.getScoreElements().put(HeaderAnnotation.SCORE_ENTITY_DISAMB,
                    0.0);
            ha.getScoreElements().put(HeaderAnnotation.SCORE_ENTITY_VOTE,
                    0.0);
            ha.getScoreElements().put(HeaderAnnotation.SUM_ENTITY_DISAMB,
                    0.0);
            ha.getScoreElements().put(HeaderAnnotation.SUM_ENTITY_VOTE,
                    0.0);
        }
    }

    //WARNING: CURRENTLY updating does not ADD new headers
    public void update_typing_annotations_all_candidate_contribute(List<Integer> rowsUpdated,
                                                                   int column,
                                                                   TAnnotation table_annotations,
                                                                   Table table,
                                                                   int tableRowsTotal) {
        HeaderAnnotation[] existing_header_annotations = table_annotations.getHeaderAnnotation(column);
        existing_header_annotations = existing_header_annotations == null ? new HeaderAnnotation[0] : existing_header_annotations;
        reset_entity_contributed_scores(existing_header_annotations);
        //supporting rows are only added if a header for the type of the cell annotation exists

        for (int row : rowsUpdated) {
            TCellAnnotation[] cellAnnotations = table_annotations.getContentCellAnnotations(row, column);

            Map<String, Double> header_annotation_url_and_max_score = new HashMap<String, Double>();
            Map<String, String> header_annotation_url_and_label = new HashMap<String, String>();
            for (TCellAnnotation ca : cellAnnotations) {
                List<Clazz> types = ca.getAnnotation().getTypes();
                double disamb_score = ca.getFinalScore();
                for (Clazz t : types) {
                    String url = t.getId();
                    String label = t.getLabel();
                    header_annotation_url_and_label.put(url, label);
                    Double score = header_annotation_url_and_max_score.get(url);
                    if (score == null) score = 0.0;
                    if (disamb_score > score) {
                        /*if(score!=0)
                        System.out.println();*/
                        score = disamb_score;
                    }
                    header_annotation_url_and_max_score.put(url, score);
                }
            }

            Set<HeaderAnnotation> new_header_annotation_placeholders = new HashSet<HeaderAnnotation>();
            HeaderAnnotationUpdater.add(header_annotation_url_and_label,
                    column,
                    table,
                    existing_header_annotations,
                    new_header_annotation_placeholders);
            if (TableMinerConstants.ALLOW_NEW_HEADERS_AT_DISAMBIGUATION_UPDATE) {
                for (HeaderAnnotation ha : existing_header_annotations)
                    new_header_annotation_placeholders.add(ha);
                existing_header_annotations = new_header_annotation_placeholders.toArray(new HeaderAnnotation[0]);
            }

            HeaderAnnotationUpdater.update_by_entity_contribution(
                    header_annotation_url_and_max_score, row, existing_header_annotations
            );
            //p.close();

        }

        Set<HeaderAnnotation> headers = new HashSet<HeaderAnnotation>(Arrays.asList(existing_header_annotations));
        headers = classification_scorer.score_context(
                headers, table, column, false);

        //final update to compute revised typing scores, then sort them
        List<HeaderAnnotation> resort = new ArrayList<HeaderAnnotation>();
        for (HeaderAnnotation ha : headers) {
            classification_scorer.compute_final_score(ha, tableRowsTotal);
            /* ha.setScoreElements(revised_score_elements);
            ha.setFinalScore(revised_score_elements.get(HeaderAnnotation.FINAL));*/
            resort.add(ha);
        }

        Collections.sort(resort);
        table_annotations.setHeaderAnnotation(column, resort.toArray(new HeaderAnnotation[0]));

    }

    private List<Pair<Entity, Map<String, Double>>> disambiguate(Set<String> already_built_feature_space_entity_candidates,
                                                                            TContentCell tcc,
                                                                            Table table,
                                                                            Set<String> columnTypes,
                                                                            List<Integer> table_cell_rows,
                                                                            int table_cell_col,
                                                                 Entity... reference_disambiguated_entities) throws IOException {
        List<Pair<Entity, Map<String, Double>>> candidates_and_scores_for_block
                = new ArrayList<>();

        List<Entity> candidates = kbSearch.findEntityCandidatesOfTypes(tcc.getText(), columnTypes.toArray(new String[0]));

        int count_already_built_feature_space = 0;
        for (Resource ec : candidates) {
            if (already_built_feature_space_entity_candidates.contains(ec.getId()))
                count_already_built_feature_space++;
        }

        System.out.println("(ALREADY BUILT FOR=" + count_already_built_feature_space);

        if (candidates != null && candidates.size() != 0) {
        } else {
            candidates = kbSearch.findEntityCandidatesOfTypes(tcc.getText());
        }

        //now each candidate is given scores
        candidates_and_scores_for_block =
                disambiguator.disambiguate_learn_consolidate
                        (candidates, table, table_cell_rows, table_cell_col, columnTypes, false, reference_disambiguated_entities);

        return candidates_and_scores_for_block;
    }

    private void update_entity_annotations(
            Table table,
            TAnnotation table_annotation,
            List<Integer> table_cell_rows,
            int table_cell_col,
            List<Pair<Entity, Map<String, Double>>> candidates_and_scores_for_cell) {

        Collections.sort(candidates_and_scores_for_cell, (o1, o2) -> {
            Double o2_score = o2.getValue().get(TCellAnnotation.SCORE_FINAL);
            Double o1_score = o1.getValue().get(TCellAnnotation.SCORE_FINAL);
            return o2_score.compareTo(o1_score);
        });

        String sampleCellText = table.getContentCell(table_cell_rows.get(0), table_cell_col).getText();

        for (int row : table_cell_rows) {
            TCellAnnotation[] annotationsForCell = new TCellAnnotation[candidates_and_scores_for_cell.size()];
            for (int i = 0; i < candidates_and_scores_for_cell.size(); i++) {
                Pair<Entity, Map<String, Double>> e = candidates_and_scores_for_cell.get(i);
                annotationsForCell[i] = new TCellAnnotation(sampleCellText,
                        e.getKey(), e.getValue().get("final"), e.getValue());
                /*if(table_cell_row==5 &&table_cell_col==4)
                System.out.println(i);*/
            }

            table_annotation.setContentCellAnnotations(row, table_cell_col, annotationsForCell);
        }
        /* if (table_cell_row == 5 && table_cell_col == 4)
        System.out.println("end");*/
    }
}
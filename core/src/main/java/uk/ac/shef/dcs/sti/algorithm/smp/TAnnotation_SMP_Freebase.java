package uk.ac.shef.dcs.sti.algorithm.smp;

import uk.ac.shef.dcs.sti.rep.TColumnHeaderAnnotation;
import uk.ac.shef.dcs.sti.rep.TAnnotation;

import java.util.*;

/**
 * SMP uses granularity of a concept. In freebase, not every concept has instances.
 * some concepts are classified as "topic" and not properly as a concept, and therefore, they have no instances
 * Currently #KBSearcher_Freebase simply returns "1" for such concepts, effectively they have the maximum granularity.
 * This put other concepts that do have instances (e.g., /location/location) at disadvantage.
 *
 * This class extends TAnnotation and overwrites method #getBestHeaderAnnotations to cope with such problems.
 * Effectively, the "real" freebase concept that has the smallest number of instances (highest granularity) and any
 * "topic-based" concept (/m/*) are both considered the best header annotations
 */
public class TAnnotation_SMP_Freebase extends TAnnotation {
    public TAnnotation_SMP_Freebase(int rows, int cols) {
        super(rows, cols);
    }

    public TColumnHeaderAnnotation[] getHeaderAnnotation(int headerCol){
        Object o=headerAnnotations.get(headerCol);
        if(o==null)
            return new TColumnHeaderAnnotation[0];
        TColumnHeaderAnnotation[] ha = (TColumnHeaderAnnotation[]) o;
        Arrays.sort(ha, new Comparator<TColumnHeaderAnnotation>() {
            @Override
            public int compare(TColumnHeaderAnnotation o1, TColumnHeaderAnnotation o2) {
                int compared = ((Double) o2.getFinalScore()).compareTo(o1.getFinalScore());
                if (compared == 0) {
                    Double o1_granularity = o1.getScoreElements().get(ColumnClassifier.SMP_SCORE_GRANULARITY);
                    Double o2_granularity = o2.getScoreElements().get(ColumnClassifier.SMP_SCORE_GRANULARITY);
                    return o1_granularity.compareTo(o2_granularity);
                }
                return compared;
            }
        });

        return ha;
    }

    public List<TColumnHeaderAnnotation> getBestHeaderAnnotations(int headerCol){
        TColumnHeaderAnnotation[] annotations =getHeaderAnnotation(headerCol);
        List<TColumnHeaderAnnotation> result = new ArrayList<TColumnHeaderAnnotation>();
        if(annotations==null||annotations.length==0)
            return result;

        List<TColumnHeaderAnnotation> sorted = Arrays.asList(annotations);
        Collections.sort(sorted);

        //container to keep temporarily any concepts that have real instances and computeElementScores the same "entity computeElementScores)
        List<TColumnHeaderAnnotation> tmp = new ArrayList<TColumnHeaderAnnotation>();
        double maxScore = sorted.get(0).getFinalScore();
        for(TColumnHeaderAnnotation h: sorted){
            if(h.getFinalScore()==maxScore){
                if(h.getAnnotation().getId().startsWith("/m/"))
                    result.add(h);
                else{
                    tmp.add(h);
                }
            }
        }

        if(tmp.size()>1){
            Collections.sort(tmp, new Comparator<TColumnHeaderAnnotation>() {
                @Override
                public int compare(TColumnHeaderAnnotation o1, TColumnHeaderAnnotation o2) {
                    return o1.getScoreElements().get(ColumnClassifier.SMP_SCORE_GRANULARITY).compareTo(
                            o2.getScoreElements().get(ColumnClassifier.SMP_SCORE_GRANULARITY)
                    );
                }
            });
            Double highest_granularity_score = tmp.get(0).getScoreElements().get(ColumnClassifier.SMP_SCORE_GRANULARITY);
            for(TColumnHeaderAnnotation ha: tmp){
                if(ha.getScoreElements().get(ColumnClassifier.SMP_SCORE_GRANULARITY)==highest_granularity_score)
                    result.add(ha);
            }
        }else{
            result.addAll(tmp);
        }

        return result;
    }
}

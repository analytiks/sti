package uk.ac.shef.dcs.kbsearch.freebase;

import com.google.api.client.http.HttpResponseException;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import uk.ac.shef.dcs.kbsearch.KBSearch;
import uk.ac.shef.dcs.kbsearch.KBSearchException;
import uk.ac.shef.dcs.kbsearch.rep.Attribute;
import uk.ac.shef.dcs.kbsearch.rep.Clazz;
import uk.ac.shef.dcs.kbsearch.rep.Entity;
import uk.ac.shef.dcs.util.SolrCache;
import uk.ac.shef.dcs.util.StringUtils;

import java.io.IOException;
import java.util.*;


/**
 */
public class FreebaseSearch extends KBSearch {

    private static final Logger LOG = Logger.getLogger(FreebaseSearch.class.getName());
    public static String PROPERTY_SIMILARITY_CACHE_CORENAME = "similarity";
    private static final boolean AUTO_COMMIT = true;

    //two propperties for debugging purposes.In practice both should be false. set to true
    //if you want to deliberately trigger calls to FB apis
    private static final boolean ALWAYS_CALL_REMOTE_SEARCHAPI = false;
    private static final boolean ALWAYS_CALL_REMOTE_TOPICAPI = false;
    private FreebaseQueryProxy searcher;

    protected Map<String, SolrCache> otherCache;


    public FreebaseSearch(String kbSearchPropertyFile, Boolean fuzzyKeywords,
                          EmbeddedSolrServer cacheEntity, EmbeddedSolrServer cacheConcept,
                          EmbeddedSolrServer cacheProperty) throws IOException {
        super(kbSearchPropertyFile, fuzzyKeywords, cacheEntity, cacheConcept, cacheProperty);
        searcher = new FreebaseQueryProxy(properties);
        otherCache = new HashMap<>();
        resultFilter = new FreebaseSearchResultFilter(properties.getProperty(KB_SEARCH_RESULT_STOPLIST));
    }

    public void registerOtherCache(String name, EmbeddedSolrServer cacheServer) {
        otherCache.put(name, new SolrCache(cacheServer));
    }

    @Deprecated
    public List<Clazz> find_typesForEntity_filtered(String id) throws KBSearchException {
        String query = createSolrCacheQuery_findClazzesOfResource(id);
        List<Clazz> result = null;
        try {
            result = (List<Clazz>) cacheEntity.retrieve(query);
            if (result != null) {
                LOG.info("QUERY (cache load)=" + query + "|" + query);
            }
        } catch (Exception e) {
        }
        if (result == null) {
            result = new ArrayList<>();
            try {
                List<Attribute> attributes = searcher.topicapi_getTypesOfTopicID(id);
                for (Attribute attr : attributes) {
                    String type = attr.getValueURI(); //this is the id of the type
                    result.add(new Clazz(type, attr.getValue()));
                }

                cacheEntity.cache(query, result, AUTO_COMMIT);
                // debug_helper_method(id, facts);
                LOG.info("QUERY (cache save)=" + query + "|" + query);
            } catch (Exception e) {
                throw new KBSearchException(e);
            }
        }
        return getResultFilter().filterClazz(result);
    }

    @Override
    public List<Entity> findEntityCandidates(String content) throws KBSearchException {
        return find_matchingEntitiesForTextAndType(content);
    }

    @Override
    public List<Entity> findEntityCandidatesOfTypes(String content, String... types) throws KBSearchException {
        return find_matchingEntitiesForTextAndType(content, types);
    }

    @Override
    public List<Attribute> findAttributesOfEntities(Entity ec) throws KBSearchException {
        return find_attributes(ec.getId(), cacheEntity);
    }

    @Override
    public List<Attribute> findAttributesOfProperty(String propertyId) throws KBSearchException {
        return find_attributes(propertyId, cacheProperty);
    }


    private List<Entity> find_matchingEntitiesForTextAndType(String text, String... types) throws KBSearchException {
        String query = createSolrCacheQuery_findResources(text);
        ;
        boolean forceQuery = false;
        text = StringEscapeUtils.unescapeXml(text);
        int bracket = text.indexOf("(");
        if (bracket != -1) {
            text = text.substring(0, bracket).trim();
        }
        if (StringUtils.toAlphaNumericWhitechar(text).trim().length() == 0)
            return new ArrayList<>();
        if (ALWAYS_CALL_REMOTE_SEARCHAPI)
            forceQuery = true;

        List<Entity> result = null;
        if (!forceQuery) {
            try {
                result = (List<Entity>) cacheEntity.retrieve(query);
                if (result != null)
                    LOG.info("QUERY (cache load)=" + query + "|" + query);
            } catch (Exception e) {
            }
        }
        if (result == null) {
            result = new ArrayList<>();
            try {
                //firstly fetch candidate freebase topics. pass 'true' to only keep candidates whose name overlap with the query term
                List<FreebaseTopic> topics = searcher.searchapi_getTopicsByNameAndType(text, "any", true, 20); //search api does not retrieve complete types, find types for them
                for (FreebaseTopic ec : topics) {
                    //Next get attributes for each topic
                    List<Attribute> attributes = findAttributesOfEntities(ec);
                    ec.setAttributes(attributes);
                    for (Attribute attr : attributes) {
                        if (attr.getRelation().equals(FreebaseEnum.RELATION_HASTYPE.getString()) &&
                                attr.isDirect() &&
                                !ec.hasType(attr.getValueURI()))
                            ec.addType(new Clazz(attr.getValueURI(), attr.getValue()));
                    }
                }

                if (topics.size() == 0 && fuzzyKeywords) { //does the query has conjunection word? if so, we may need to try again with split queries
                    String[] queries = text.split("\\band\\b");
                    if (queries.length < 2) {
                        queries = text.split("\\bor\\b");
                        if (queries.length < 2) {
                            queries = text.split("/");
                            if (queries.length < 2) {
                                queries = text.split(",");
                            }
                        }
                    }
                    if (queries.length > 1) {
                        for (String q : queries) {
                            q = q.trim();
                            if (q.length() < 1) continue;
                            result.addAll(find_matchingEntitiesForTextAndType(q, types));
                        }
                    }
                }

                result.addAll(topics);
                cacheEntity.cache(query, result, AUTO_COMMIT);
                LOG.info("QUERY (cache save)=" + query + "|" + query);
            } catch (Exception e) {
                throw new KBSearchException(e);
            }
        }

        int beforeFiltering = result.size();
        if (types.length > 0) {
            Iterator<Entity> it = result.iterator();
            while (it.hasNext()) {
                Entity ec = it.next();
                boolean typeSatisfied = false;
                for (String t : types) {
                    if (ec.hasType(t)) {
                        typeSatisfied = true;
                        break;
                    }
                }
                if (!typeSatisfied)
                    it.remove();
            }
        }

        //filter entity's clazz, and attributes
        String id = "|";
        for (Entity ec : result) {
            id = id + ec.getId() + ",";
            //ec.setTypes(FreebaseSearchResultFilter.filterClazz(ec.getTypes()));
            List<Clazz> filteredTypes = getResultFilter().filterClazz(ec.getTypes());
            ec.clearTypes();
            for (Clazz ft : filteredTypes)
                ec.addType(ft);

            //no need to filter attributes as "find_attributes" method already does it
            /*List<Attribute> filteredAttributes = getResultFilter().filterAttribute(ec.getAttributes());
            ec.getAttributes().clear();
            ec.getAttributes().addAll(filteredAttributes);*/
        }

        //LOG.info("(QUERY_KB:" + beforeFiltering + " => " + result.size() + id);
        return result;
    }


    /*
    In FB, getting the attributes of a class is different from that for entities and properties, we need to implement it differently
    and cannot use find_attributes method
     */
    @Override
    public List<Attribute> findAttributesOfClazz(String clazz) throws KBSearchException {
        //return find_triplesForEntity(conceptId);
        boolean forceQuery = false;
        if (ALWAYS_CALL_REMOTE_TOPICAPI)
            forceQuery = true;
        List<Attribute> attributes = new ArrayList<>();
        String query = createSolrCacheQuery_findAttributesOfResource(clazz);
        if (query.length() == 0) return attributes;

        try {
            attributes = (List<Attribute>) cacheConcept.retrieve(query);
            if (attributes != null)
                LOG.info("QUERY (cache load)=" + query + "|" + query);
        } catch (Exception e) {
        }

        if (attributes == null || forceQuery) {
            try {
                attributes = new ArrayList<>();
                List<Attribute> retrievedAttributes = searcher.topicapi_getAttributesOfTopic(clazz);
                //check firstly, is this a concept?
                boolean isConcept = false;
                for (Attribute f : retrievedAttributes) {
                    if (f.getRelation().equals(FreebaseEnum.RELATION_HASTYPE.getString())
                            && f.getValueURI() != null && f.getValueURI().equals(FreebaseEnum.TYPE_TYPE.getString())) {
                        isConcept = true;
                        break;
                    }
                }
                if (!isConcept) {
                    try {
                        cacheConcept.cache(query, attributes, AUTO_COMMIT);
                        LOG.info("QUERY (cache save)=" + query + "|" + query);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return attributes;
                }

                //ok, this is a concept. We need to deep-fetch its properties, and find out the range of their properties
                for (Attribute f : retrievedAttributes) {
                    if (f.getRelation().equals(FreebaseEnum.TYPE_PROPERTYOFTYPE.getString())) { //this is a property of a concept, we need to process it further
                        String propertyId = f.getValueURI();
                        if (propertyId == null) continue;

                        List<Attribute> attrOfProperty = findAttributesOfProperty(propertyId);
                        for (Attribute t : attrOfProperty) {
                            if (t.getRelation().equals(FreebaseEnum.RELATION_RANGEOFPROPERTY.getString())) {
                                String rangeLabel = t.getValue();
                                String rangeURL = t.getValueURI();
                                Attribute attr = new Attribute(f.getValueURI(), rangeLabel);
                                attr.setValueURI(rangeURL);
                                attr.setIsDirect(true);
                                //attributes.add(new String[]{f[2], rangeLabel, rangeURL, "n"});
                            }
                        }
                    } else {
                        attributes.add(f);
                    }
                }

                cacheConcept.cache(query, attributes, AUTO_COMMIT);
                LOG.info("QUERY (cache save)=" + query + "|" + query);
            } catch (Exception e) {
                throw new KBSearchException(e);
            }
        }

        //filtering
        attributes=getResultFilter().filterAttribute(attributes);
        return attributes;
    }

    @Override
    public double findGranularityOfClazz(String clazz) throws KBSearchException {
        String query = createSolrCacheQuery_findGranularityOfClazz(clazz);
        Double result = null;
        try {
            Object o = cacheConcept.retrieve(query);
            if (o != null) {
                LOG.info("QUERY (cache load)=" + query + "|" + clazz);
                return (Double) o;
            }
        } catch (Exception e) {
        }

        if (result == null) {
            try {
                double granularity = searcher.find_granularityForType(clazz);
                result = granularity;
                try {
                    cacheConcept.cache(query, result, AUTO_COMMIT);
                    LOG.info("QUERY (cache save)=" + query + "|" + clazz);
                } catch (Exception e) {
                    LOG.error("FAILED:" + clazz);
                    e.printStackTrace();
                }
            } catch (IOException ioe) {
                LOG.info("ERROR(Instances of Type): Unable to fetch freebase page of instances of type: " + clazz);
            }
        }
        if (result == null)
            return -1.0;
        return result;
    }

    @Override
    public List<Clazz> findRangeOfRelation(String relationURI) throws KBSearchException {
        List<Attribute> attributes =
                findAttributesOfEntities(new Entity(relationURI, relationURI));
        List<Clazz> types = new ArrayList<>();
        for (Attribute attr : attributes) {
            if (attr.getRelation().equals(FreebaseEnum.RELATION_RANGEOFPROPERTY.getString())) {
                types.add(new Clazz(attr.getValueURI(), attr.getValue()));
            }
        }
        return types;
    }


    public double findEntityConceptSimilarity(String id1, String id2) {
        String query = createSolrCacheQuery_findEntityConceptSimilarity(id1, id2);
        Object result = null;
        try {
            result = otherCache.get(PROPERTY_SIMILARITY_CACHE_CORENAME).retrieve(query);
            if (result != null)
                LOG.info("QUERY (cache load)=" + query + "|" + query);
        } catch (Exception e) {
        }
        if (result == null)
            return -1.0;
        return (Double) result;
    }

    public void cacheEntityConceptSimilarity(String id1, String id2, double score, boolean biDirectional,
                                             boolean commit) {
        String query = createSolrCacheQuery_findEntityConceptSimilarity(id1, id2);
        try {
            otherCache.get(PROPERTY_SIMILARITY_CACHE_CORENAME).cache(query, score, commit);
            LOG.info("QUERY (cache saving)=" + query + "|" + query);
            if (biDirectional) {
                query = id2 + "<>" + id1;
                otherCache.get(PROPERTY_SIMILARITY_CACHE_CORENAME).cache(query, score, commit);
                LOG.info("QUERY (cache saving)=" + query + "|" + query);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Attribute> find_attributes(String id, SolrCache cache) throws KBSearchException {
        if (id.length() == 0)
            return new ArrayList<>();
        boolean forceQuery = false;
        if (ALWAYS_CALL_REMOTE_TOPICAPI)
            forceQuery = true;

        String query = createSolrCacheQuery_findAttributesOfResource(id);
        List<Attribute> result = null;
        try {
            result = (List<Attribute>) cache.retrieve(query);
            if (result != null)
                LOG.info("QUERY (cache load)=" + query + "|" + query);
        } catch (Exception e) {
        }
        if (result == null || forceQuery) {
            List<Attribute> attributes;
            try {
                attributes = searcher.topicapi_getAttributesOfTopic(id);
            } catch (Exception e) {
                if (e instanceof HttpResponseException && donotRepeatQuery((HttpResponseException) e))
                    attributes = new ArrayList<>();
                else
                    throw new KBSearchException(e);
            }
            result = new ArrayList<>();
            result.addAll(attributes);
            try {
                cache.cache(query, result, AUTO_COMMIT);
                LOG.info("QUERY (cache save)=" + query + "|" + query);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //filtering
        result = getResultFilter().filterAttribute(result);
        return result;
    }

    public void commitChanges() throws KBSearchException {
        try {
            cacheConcept.commit();
            cacheEntity.commit();
            cacheProperty.commit();
            for (SolrCache cache : otherCache.values())
                cache.commit();
        } catch (Exception e) {
            throw new KBSearchException(e);
        }
    }

    private boolean donotRepeatQuery(HttpResponseException e) {
        String message = e.getContent();
        if (message.contains("\"reason\": \"notFound\""))
            return true;
        return false;
    }


    @Override
    public void closeConnection() throws KBSearchException {
        try {
            if (cacheEntity != null)
                cacheEntity.shutdown();
            if (cacheConcept != null)
                cacheConcept.shutdown();
            if (cacheProperty != null)
                cacheProperty.shutdown();
        } catch (Exception e) {
            throw new KBSearchException(e);
        }
    }
}

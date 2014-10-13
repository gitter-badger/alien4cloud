package alien4cloud.dao;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.SneakyThrows;

import org.elasticsearch.action.count.CountRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.mapping.FilterValuesStrategy;
import org.elasticsearch.mapping.MappingBuilder;
import org.elasticsearch.mapping.QueryHelper;
import org.elasticsearch.mapping.QueryHelper.SearchQueryHelperBuilder;
import org.elasticsearch.mapping.SourceFetchContext;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.terms.TermsFacet;

import alien4cloud.dao.model.FacetedSearchFacet;
import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.dao.model.GetMultipleDataResult;

import com.google.common.collect.Lists;

/**
 * Elastic search dao that manages search operations.
 *
 * @author luc boutier
 */
public class ESGenericSearchDAO extends ESGenericIdDAO implements IGenericSearchDAO {
    private static final String SCORE_SCRIPT = "_score * ((doc.containsKey('alienScore') && !doc['alienScore'].empty) ? doc['alienScore'].value : 1)";

    @Resource
    private QueryHelper queryHelper;

    @Override
    public <T> long count(Class<T> clazz, QueryBuilder query) {
        String indexName = getIndexForType(clazz);
        String typeName = MappingBuilder.indexTypeFromClass(clazz);
        CountRequestBuilder countRequestBuilder = getClient().prepareCount(indexName).setTypes(typeName);
        if (query != null) {
            countRequestBuilder.setQuery(query);
        }
        return countRequestBuilder.execute().actionGet().getCount();
    }

    @Override
    public <T> long count(Class<T> clazz, String searchText, Map<String, String[]> filters) {
        String[] searchIndexes = clazz == null ? getAllIndexes() : new String[] { getIndexForType(clazz) };
        Class<?>[] requestedTypes = getRequestedTypes(clazz);

        return this.queryHelper.buildCountQuery(searchIndexes, searchText).types(requestedTypes).filters(filters).count().getCount();
    }

    @SneakyThrows({ IOException.class })
    private <T> List<T> doCustomFind(Class<T> clazz, QueryBuilder query, int size) {
        String indexName = getIndexForType(clazz);
        String typeName = MappingBuilder.indexTypeFromClass(clazz);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(indexName).setTypes(typeName).setSize(size);
        if (query != null) {
            searchRequestBuilder.setQuery(query);
        }
        SearchResponse response = searchRequestBuilder.execute().actionGet();
        if (!somethingFound(response)) {
            return null;
        } else {
            List<T> hits = Lists.newArrayList();
            for (int i = 0; i < response.getHits().getHits().length; i++) {
                String hit = response.getHits().getAt(i).sourceAsString();
                hits.add(getJsonMapper().readValue(hit, clazz));
            }
            return hits;
        }
    }

    @Override
    public <T> T customFind(Class<T> clazz, QueryBuilder query) {
        List<T> results = doCustomFind(clazz, query, 1);
        if (results == null || results.isEmpty()) {
            return null;
        } else {
            return results.iterator().next();
        }
    }

    @Override
    public <T> List<T> customFindAll(Class<T> clazz, QueryBuilder query) {
        return doCustomFind(clazz, query, Integer.MAX_VALUE);
    }

    @Override
    public <T> GetMultipleDataResult<T> find(Class<T> clazz, Map<String, String[]> filters, int maxElements) {
        return find(clazz, filters, 0, maxElements);
    }

    @Override
    public <T> GetMultipleDataResult<T> find(Class<T> clazz, Map<String, String[]> filters, int from, int maxElements) {
        return search(clazz, null, filters, from, maxElements);
    }

    @Override
    public GetMultipleDataResult<Object> search(SearchQueryHelperBuilder queryHelperBuilder, int from, int maxElements) {
        return toGetMultipleDataResult(Object.class, queryHelperBuilder.search(from, maxElements), from);
    }

    @Override
    public <T> GetMultipleDataResult<T> search(Class<T> clazz, String searchText, Map<String, String[]> filters, int maxElements) {
        return search(clazz, searchText, filters, 0, maxElements);
    }

    @Override
    public <T> GetMultipleDataResult<T> search(Class<T> clazz, String searchText, Map<String, String[]> filters, int from, int maxElements) {
        return search(clazz, searchText, filters, null, from, maxElements);
    }

    @Override
    public <T> GetMultipleDataResult<T> search(Class<T> clazz, String searchText, Map<String, String[]> filters, String fetchContext, int from, int maxElements) {
        return search(clazz, searchText, filters, null, fetchContext, from, maxElements);
    }

    @Override
    public <T> GetMultipleDataResult<T> search(Class<T> clazz, String searchText, Map<String, String[]> filters, FilterBuilder customFilter,
            String fetchContext, int from, int maxElements) {
        SearchResponse searchResponse = doSearch(clazz, searchText, filters, customFilter, fetchContext, from, maxElements, false);
        return toGetMultipleDataResult(clazz, searchResponse, from);
    }

    @Override
    public GetMultipleDataResult<Object> search(String[] searchIndices, Class<?>[] classes, String searchText, Map<String, String[]> filters,
            String fetchContext, int from, int maxElements) {
        return search(searchIndices, classes, searchText, filters, null, fetchContext, from, maxElements);
    }

    @Override
    public GetMultipleDataResult<Object> search(String[] searchIndices, Class<?>[] classes, String searchText, Map<String, String[]> filters,
            FilterBuilder customFilter, String fetchContext, int from, int maxElements) {
        SearchResponse searchResponse = queryHelper.buildSearchQuery(searchIndices, searchText).fetchContext(fetchContext).filters(filters)
                .customFilter(customFilter).types(classes).search(from, maxElements);
        return toGetMultipleDataResult(Object.class, searchResponse, from);
    }

    @Override
    public <T> FacetedSearchResult facetedSearch(Class<T> clazz, String searchText, Map<String, String[]> filters, int maxElements) {
        return facetedSearch(clazz, searchText, filters, null, 0, maxElements);
    }

    @Override
    public <T> FacetedSearchResult facetedSearch(Class<T> clazz, String searchText, Map<String, String[]> filters, String fetchContext, int from,
            int maxElements) {
        return facetedSearch(clazz, searchText, filters, null, fetchContext, from, maxElements);
    }

    @Override
    @SneakyThrows({ IOException.class })
    public <T> FacetedSearchResult facetedSearch(Class<T> clazz, String searchText, Map<String, String[]> filters, FilterBuilder customFilter,
            String fetchContext, int from, int maxElements) {
        SearchResponse searchResponse = doSearch(clazz, searchText, filters, customFilter, fetchContext, from, maxElements, true);

        // check something found
        // return an empty object if nothing found
        if (!somethingFound(searchResponse)) {
            FacetedSearchResult toReturn = new FacetedSearchResult(from, 0, 0, 0, new String[0], new String[0], new HashMap<String, FacetedSearchFacet[]>());
            if (searchResponse != null) {
                toReturn.setQueryDuration(searchResponse.getTookInMillis());
            }
            return toReturn;
        }

        FacetedSearchResult finalResponse = new FacetedSearchResult();

        fillMultipleDataResult(clazz, searchResponse, finalResponse, from, true);

        finalResponse.setFacets(parseFacets(searchResponse.getFacets()));

        return finalResponse;
    }

    @Override
    public GetMultipleDataResult<Object> suggestSearch(String[] searchIndices, Class<?>[] requestedTypes, String suggestFieldPath, String searchPrefix,
            String fetchContext, int from, int maxElements) {
        SearchResponse searchResponse = queryHelper.buildSearchSuggestQuery(searchIndices, searchPrefix, suggestFieldPath).types(requestedTypes)
                .fetchContext(fetchContext).search(from, maxElements);

        return toGetMultipleDataResult(Object.class, searchResponse, from);
    }

    @Override
    public <T> GetMultipleDataResult<T> search(Class<T> clazz, String searchText, Map<String, String[]> filters,
            Map<String, FilterValuesStrategy> filterStrategies, int maxElements) {
        String[] searchIndices = clazz == null ? getAllIndexes() : new String[] { getIndexForType(clazz) };
        Class<?>[] requestedTypes = getRequestedTypes(clazz);

        SearchResponse searchResponse = queryHelper.buildSearchQuery(searchIndices).types(requestedTypes).filters(filters).filterStrategies(filterStrategies)
                .search(0, maxElements);

        return toGetMultipleDataResult(clazz, searchResponse, 0);
    }

    /**
     * Convert a SearchResponse into a {@link GetMultipleDataResult} including json deserialization.
     *
     * @param searchResponse The actual search response from elastic-search.
     * @param from The start index of the search request.
     * @return A {@link GetMultipleDataResult} instance that contains de-serialized data.
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows({ IOException.class })
    public <T> GetMultipleDataResult<T> toGetMultipleDataResult(Class<T> clazz, SearchResponse searchResponse, int from) {
        // return an empty object if no data has been found in elastic search.
        if (!somethingFound(searchResponse)) {
            return new GetMultipleDataResult<T>(new String[0], (T[]) Array.newInstance(clazz, 0));
        }

        GetMultipleDataResult<T> finalResponse = new GetMultipleDataResult<T>();
        fillMultipleDataResult(clazz, searchResponse, finalResponse, from, true);

        return finalResponse;
    }

    /**
     * Convert a SearchResponse into a list of objects (json deserialization.)
     *
     * @param searchResponse The actual search response from elastic-search.
     * @param clazz The type of objects to de-serialize.
     * @return A list of instances that contains de-serialized data.
     */
    @SneakyThrows({ IOException.class })
    public <T> List<T> toGetListOfData(SearchResponse searchResponse, Class<T> clazz) {
        // return null if no data has been found in elastic search.
        if (!somethingFound(searchResponse)) {
            return null;
        }

        List<T> result = new ArrayList<>();

        for (int i = 0; i < searchResponse.getHits().getHits().length; i++) {
            result.add(getJsonMapper().readValue(searchResponse.getHits().getAt(i).getSourceAsString(), clazz));
        }

        return result;
    }

    private <T> void fillMultipleDataResult(Class<T> clazz, SearchResponse searchResponse, GetMultipleDataResult<T> finalResponse, int from,
            boolean managePagination) throws IOException {
        if (managePagination) {
            int to = from + searchResponse.getHits().getHits().length - 1;
            finalResponse.setFrom(from);
            finalResponse.setTo(to);
            finalResponse.setTotalResults(searchResponse.getHits().getTotalHits());
            finalResponse.setQueryDuration(searchResponse.getTookInMillis());
        }

        String[] resultTypes = new String[searchResponse.getHits().getHits().length];

        T[] resultData = (T[]) Array.newInstance(clazz, resultTypes.length);
        for (int i = 0; i < resultTypes.length; i++) {
            resultTypes[i] = searchResponse.getHits().getAt(i).getType();
            resultData[i] = (T) getJsonMapper().readValue(searchResponse.getHits().getAt(i).getSourceAsString(), getClassFromType(resultTypes[i]));
        }
        finalResponse.setData(resultData);

        finalResponse.setTypes(resultTypes);
    }

    private <T> SearchResponse doSearch(Class<T> clazz, String searchText, Map<String, String[]> filters, FilterBuilder customFilter, String fetchContext,
            int from, int maxElements, boolean enableFacets) {
        String[] searchIndexes = clazz == null ? getAllIndexes() : new String[] { getIndexForType(clazz) };
        Class<?>[] requestedTypes = getRequestedTypes(clazz);

        // we use for now a generic score computation based on a alienScore field.
        return this.queryHelper.buildSearchQuery(searchIndexes, searchText).types(requestedTypes).fetchContext(fetchContext).filters(filters)
                .customFilter(customFilter).functionScore(ESGenericSearchDAO.SCORE_SCRIPT).facets(enableFacets).search(from, maxElements);
    }

    private boolean somethingFound(final SearchResponse searchResponse) {
        if (searchResponse == null || searchResponse.getHits() == null || searchResponse.getHits().getHits() == null
                || searchResponse.getHits().getHits().length == 0) {
            return false;
        }
        return true;
    }

    private Map<String, FacetedSearchFacet[]> parseFacets(Facets facets) {
        if (facets == null || facets.getFacets().size() == 0) {
            return null;
        }

        Map<String, FacetedSearchFacet[]> toReturnMap = new HashMap<>();
        FacetedSearchFacet[] fsf = null;

        for (Facet facet : facets) {
            fsf = null;
            if (facet instanceof TermsFacet) {
                TermsFacet termFacet = (TermsFacet) facet;
                TermsFacet.Entry entry;
                fsf = new FacetedSearchFacet[termFacet.getEntries().size()];
                for (int i = 0; i < fsf.length; i++) {
                    entry = termFacet.getEntries().get(i);
                    fsf[i] = new FacetedSearchFacet(entry.getTerm().string(), entry.getCount());
                }
            }
            toReturnMap.put(facet.getName(), fsf);
        }

        return toReturnMap;
    }

    @Override
    public <T> List<T> findByIdsWithContext(Class<T> clazz, String fetchContext, String... ids) {

        // get the fetch context for the given type and apply it to the search
        List<String> includes = new ArrayList<String>();
        List<String> excludes = new ArrayList<String>();
        SourceFetchContext sourceFetchContext = getMappingBuilder().getFetchSource(clazz.getName(), fetchContext);
        if (sourceFetchContext != null) {
            includes.addAll(sourceFetchContext.getIncludes());
            excludes.addAll(sourceFetchContext.getExcludes());
        } else {
            getLog().warn("Unable to find fetch context <" + fetchContext + "> for class <" + clazz.getName() + ">. It will be ignored.");
        }

        String[] inc = includes.isEmpty() ? null : includes.toArray(new String[includes.size()]);
        String[] exc = excludes.isEmpty() ? null : excludes.toArray(new String[excludes.size()]);

        // TODO: correctly manage "from" and "size"
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(getIndexForType(clazz))
                .setQuery(QueryBuilders.idsQuery(MappingBuilder.indexTypeFromClass(clazz)).ids(ids)).setFetchSource(inc, exc).setSize(20);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        return toGetListOfData(searchResponse, clazz);
    }

    @Override
    public QueryHelper getQueryHelper() {
        return this.queryHelper;
    }

}
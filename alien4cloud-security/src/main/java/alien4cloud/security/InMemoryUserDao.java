package alien4cloud.security;

import java.util.List;
import java.util.Map;

import alien4cloud.dao.model.FacetedSearchFacet;
import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.dao.model.GetMultipleDataResult;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Implementation of the {@link IAlienUserDao} that stores data in memory only. This should not be used in production.
 * 
 * @author luc boutier
 */
public class InMemoryUserDao implements IAlienUserDao {
    private Map<String, User> userMap = Maps.newHashMap();

    @Override
    public void save(User user) {
        userMap.put(user.getUsername(), user);
    }

    @Override
    public User find(String username) {
        return userMap.get(username);
    }

    @Override
    public void delete(String username) {
        userMap.remove(username);
    }

    @Override
    public FacetedSearchResult search(String searchQuery, String group, int from, int size) {
        List<User> userList = Lists.newArrayList();
        for (User user : userMap.values()) {
            if (user.getUsername().startsWith(searchQuery)) {
                userList.add(user);
            }
        }
        User[] users = userList.toArray(new User[userList.size()]);
        String[] types = new String[users.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = User.class.getName();
        }
        Map<String, FacetedSearchFacet[]> facets = Maps.newHashMap();
        return new FacetedSearchResult(0, users.length, 0, users.length, types, users, facets);
    }

    @Override
    public GetMultipleDataResult find(Map<String, String[]> filters, int maxElements) {
        return null;
    }

    @Override
    public List<User> find(String... usernames) {
        // TODO Auto-generated method stub
        return null;
    }

}
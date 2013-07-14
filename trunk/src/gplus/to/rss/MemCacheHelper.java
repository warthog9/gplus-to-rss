package gplus.to.rss;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache Helper
 */
public class MemCacheHelper {

	/** Cache instance */
	private static Map<Object, Object> cache = new HashMap<Object, Object>();

	
	/**
	 * Gets a value from cache (may return null)
	 */
	public static synchronized Object get(Object key) {
		return cache.get(key);
	}

	/**
	 * Puts a key/value pair in cache
	 */
	public static synchronized void put(Object key, Object value) {
		cache.put(key, value);
	}
	
}

/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Implements a simple cache holding a defined number of objects.
 * If an object needs to be removed from the cache, the oldest objects
 * get removed first.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class LRUCache implements Cache {

    /**
     * the maximum capacity of the cache
     */
    private final int capacity;
    /**
     * stores the cached objects along with their keys
     */
    private final Hashtable objectMap = new Hashtable();
    /**
     * points to the first inserted object which is going to be removed first
     */
    private LRUNode first = null;
    /**
     * points to the last inserted object
     */
    private LRUNode last = null;

    /**
     * Create a new cache with a given capacity
     * 
     * @param   capacity    number of objects that fit into the cache
     * @throws  IllegalArgumentException if the capacity is <= 1
     */
    public LRUCache(final int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("LRUCache capacity needs to be > 1!");
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return this.capacity;
    }

    public void clear() {
        this.objectMap.clear();
        this.first = null;
    }

    public boolean contains(final Object obj) {
        // search through all elements
        for (final Enumeration en = elements(); en.hasMoreElements();) {
            // we return success if we have found the desired element
            if (obj == en.nextElement()) {
                return true;
            }
        }

        return false;
    }

    public boolean containsKey(final Object key) {
        return this.objectMap.containsKey(key);
    }

    public Enumeration elements() {
        return new Enumeration() {

            private Enumeration en = objectMap.elements();

            public boolean hasMoreElements() {
                return this.en.hasMoreElements();
            }

            public Object nextElement() {
                return ((LRUNode) this.en.nextElement()).data;
            }
        };
    }

    public Object get(final Object key) {
        // retrieve the cached object
        final LRUNode node = (LRUNode) this.objectMap.get(key);

        if (node == null) {
            return null;
        } else {
            // put the list at the end of the list of cached objects, so that it gets removed last
            if (this.last != node) {
                this.last.next = node;
                if (this.first == node) {
                    this.first = node.next;
                    this.first.previous = null;
                } else {
                    node.previous.next = node.next;
                    node.next.previous = node.previous;
                }
                node.previous = this.last;
                node.next = null;
                this.last = node;
            }

            return node.data;
        }
    }

    public Enumeration keys() {
        return this.objectMap.keys();
    }

    public void put(final Object key, final Object obj) {
        // the new node to insert
        LRUNode node = null;

        // the cache is already full?
        if (size() >= capacity()) {
            // then first remove one element
            this.objectMap.remove(this.first.key);
            // we can reuse the removed node object
            node = this.first;
            // the following node in the list is now the next to remove
            this.first = this.first.next;
            this.first.previous = null;
        }
        // insert the new element
        if (first == null) {
            node = new LRUNode(key, obj, null, null);
            this.first = this.last = node;
        } else {
            // we can reuse an old node?
            if (node != null) {
                // yes, that's faster
                node.key = key;
                node.data = obj;
                node.previous = this.last;
                node.next = null;
            } else {
                // no, we have to create a new one
                node = new LRUNode(key, obj, this.last, null);
            }
            // append the node to the list
            this.last.next = node;
            // the new node is now the last in the list
            this.last = node;
        }

        this.objectMap.put(key, node);
    }

    public void remove(final Object key) {
        // remove the element from the cache
        final LRUNode node = (LRUNode) this.objectMap.get(key);

        this.objectMap.remove(key);
        // fix the list of cached objects
        if (node.previous != null) {
            node.previous.next = node.next;
        } else {
            this.first = node.next;
            this.first.previous = null;
        }
        if (node.next != null) {
            node.next.previous = node.previous;
        } else {
            this.last = node.previous;
            this.last.next = null;
        }
    }

    public int size() {
        return this.objectMap.size();
    }

    // data structure for the hash entries
    static class LRUNode {

        public Object key;
        public Object data;
        public LRUNode previous;
        public LRUNode next;

        public LRUNode(final Object key, final Object data, final LRUNode previous, final LRUNode next) {
            this.key = key;
            this.data = data;
            this.previous = previous;
            this.next = next;
        }
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.elasticjob.reg.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;

/**
 * Zookeeper CuratorCacheListener.
 */
public abstract class CuratorCacheListener implements TreeCacheListener {

    /**
     * An enumerated type that describes a change.
     */
    public enum Type {
        /**
         * A new node was added to the cache.
         */
        NODE_CREATED,

        /**
         * A node already in the cache has changed.
         */
        NODE_CHANGED,

        /**
         * A node already in the cache was deleted.
         */
        NODE_DELETED
    }

    @Override
    public void childEvent(final CuratorFramework client, final TreeCacheEvent event) throws Exception {
        switch (event.getType()) {
            case NODE_ADDED:
                event(Type.NODE_CREATED, null, event.getData());
                break;
            case NODE_REMOVED:
                event(Type.NODE_DELETED, event.getData(), null);
                break;
            case NODE_UPDATED:
                event(Type.NODE_CHANGED, null, event.getData());
                break;
            default:
                break;
        }
    }

    /**
     * Called when a data is created, changed or deleted.
     *
     * @param type the type of event
     * @param oldData the old data or null
     * @param data the new data or null
     */
    public abstract void event(Type type, ChildData oldData, ChildData data);

    /**
     * When the cache is started, the initial nodes are tracked and when they are finished loading
     * into the cache this method is called.
     */
    void initialized() {
        // NOP
    }

}

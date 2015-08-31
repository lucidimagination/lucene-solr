package org.apache.solr.handler.admin;

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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.Aliases;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.DocRouter;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;
import org.apache.zookeeper.KeeperException;

public class ClusterStatus {
  private final ZkStateReader zkStateReader;
  private final String collection;
  private ZkNodeProps message;

  public ClusterStatus(ZkStateReader zkStateReader, ZkNodeProps props) {
    this.zkStateReader = zkStateReader;
    this.message = props;
    collection = props.getStr(ZkStateReader.COLLECTION_PROP);

  }

  @SuppressWarnings("unchecked")
  public void getClusterStatus(NamedList results) throws KeeperException, InterruptedException {
    zkStateReader.updateClusterState(true);
    String collection = message.getStr(ZkStateReader.COLLECTION_PROP);

    // read aliases
    Aliases aliases = zkStateReader.getAliases();
    Map<String, List<String>> collectionVsAliases = new HashMap<>();
    Map<String, String> aliasVsCollections = aliases.getCollectionAliasMap();
    if (aliasVsCollections != null) {
      for (Map.Entry<String, String> entry : aliasVsCollections.entrySet()) {
        List<String> colls = StrUtils.splitSmart(entry.getValue(), ',');
        String alias = entry.getKey();
        for (String coll : colls) {
          if (collection == null || collection.equals(coll))  {
            List<String> list = collectionVsAliases.get(coll);
            if (list == null) {
              list = new ArrayList<>();
              collectionVsAliases.put(coll, list);
            }
            list.add(alias);
          }
        }
      }
    }

    Map roles = null;
    if (zkStateReader.getZkClient().exists(ZkStateReader.ROLES, true)) {
      roles = (Map) ZkStateReader.fromJSON(zkStateReader.getZkClient().getData(ZkStateReader.ROLES, null, null, true));
    }

    ClusterState clusterState = zkStateReader.getClusterState();

    // convert cluster state into a map of writable types
    byte[] bytes = ZkStateReader.toJSON(clusterState);
    Map<String, Object> stateMap = (Map<String, Object>) ZkStateReader.fromJSON(bytes);

    String shard = message.getStr(ZkStateReader.SHARD_ID_PROP);
    NamedList<Object> collectionProps = new SimpleOrderedMap<Object>();
    if (collection == null) {
      Set<String> collections = clusterState.getCollections();
      for (String name : collections) {
        Map<String, Object> collectionStatus = getCollectionStatus(stateMap, name, shard);
        if (collectionVsAliases.containsKey(name) && !collectionVsAliases.get(name).isEmpty())  {
          collectionStatus.put("aliases", collectionVsAliases.get(name));
        }
        collectionProps.add(name, collectionStatus);
      }
    } else {
      String routeKey = message.getStr(ShardParams._ROUTE_);
      if (routeKey == null) {
        Map<String, Object> collectionStatus = getCollectionStatus(stateMap, collection, shard);
        if (collectionVsAliases.containsKey(collection) && !collectionVsAliases.get(collection).isEmpty())  {
          collectionStatus.put("aliases", collectionVsAliases.get(collection));
        }
        collectionProps.add(collection, collectionStatus);
      } else {
        DocCollection docCollection = clusterState.getCollection(collection);
        DocRouter router = docCollection.getRouter();
        Collection<Slice> slices = router.getSearchSlices(routeKey, null, docCollection);
        String s = "";
        for (Slice slice : slices) {
          s += slice.getName() + ",";
        }
        if (shard != null)  {
          s += shard;
        }
        Map<String, Object> collectionStatus = getCollectionStatus(stateMap, collection, s);
        if (collectionVsAliases.containsKey(collection) && !collectionVsAliases.get(collection).isEmpty())  {
          collectionStatus.put("aliases", collectionVsAliases.get(collection));
        }
        collectionProps.add(collection, collectionStatus);
      }
    }

    List<String> liveNodes = zkStateReader.getZkClient().getChildren(ZkStateReader.LIVE_NODES_ZKNODE, null, true);

    // now we need to walk the collectionProps tree to cross-check replica state with live nodes
    crossCheckReplicaStateWithLiveNodes(liveNodes, collectionProps);

    NamedList<Object> clusterStatus = new SimpleOrderedMap<>();
    clusterStatus.add("collections", collectionProps);

    // read cluster properties
    Map clusterProps = zkStateReader.getClusterProps();
    if (clusterProps != null && !clusterProps.isEmpty())  {
      clusterStatus.add("properties", clusterProps);
    }

    // add the alias map too
    if (aliasVsCollections != null && !aliasVsCollections.isEmpty())  {
      clusterStatus.add("aliases", aliasVsCollections);
    }

    // add the roles map
    if (roles != null)  {
      clusterStatus.add("roles", roles);
    }

    // add live_nodes
    clusterStatus.add("live_nodes", liveNodes);

    results.add("cluster", clusterStatus);
  }

  /**
   * Get collection status from cluster state.
   * Can return collection status by given shard name.
   *
   *
   * @param clusterState cloud state map parsed from JSON-serialized {@link ClusterState}
   * @param name  collection name
   * @param shardStr comma separated shard names
   * @return map of collection properties
   */
  private Map<String, Object> getCollectionStatus(Map<String, Object> clusterState, String name, String shardStr) {
    Map<String, Object> docCollection = (Map<String, Object>) clusterState.get(name);
    if (docCollection == null)  {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Collection: " + name + " not found");
    }
    if (shardStr == null) {
      return docCollection;
    } else {
      Map<String, Object> shards = (Map<String, Object>) docCollection.get("shards");
      Map<String, Object>  selected = new HashMap<>();
      List<String> selectedShards = Arrays.asList(shardStr.split(","));
      for (String selectedShard : selectedShards) {
        if (!shards.containsKey(selectedShard)) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Collection: " + name + " shard: " + selectedShard + " not found");
        }
        selected.put(selectedShard, shards.get(selectedShard));
        docCollection.put("shards", selected);
      }
      return docCollection;
    }
  }

  /**
   * Walks the tree of collection status to verify that any replicas not reporting a "down" status is
   * on a live node, if any replicas reporting their status as "active" but the node is not live is
   * marked as "down"; used by CLUSTERSTATUS.
   * @param liveNodes List of currently live node names.
   * @param collectionProps Map of collection status information pulled directly from ZooKeeper.
   */
  protected void crossCheckReplicaStateWithLiveNodes(List<String> liveNodes, NamedList<Object> collectionProps) {
    Iterator<Map.Entry<String,Object>> colls = collectionProps.iterator();
    while (colls.hasNext()) {
      Map.Entry<String,Object> next = colls.next();
      Map<String,Object> collMap = (Map<String,Object>)next.getValue();
      Map<String,Object> shards = (Map<String,Object>)collMap.get("shards");
      for (Object nextShard : shards.values()) {
        Map<String,Object> shardMap = (Map<String,Object>)nextShard;
        Map<String,Object> replicas = (Map<String,Object>)shardMap.get("replicas");
        for (Object nextReplica : replicas.values()) {
          Map<String,Object> replicaMap = (Map<String,Object>)nextReplica;
          if (!ZkStateReader.DOWN.equals(replicaMap.get(ZkStateReader.STATE_PROP))) {
            // not down, so verify the node is live
            String node_name = (String)replicaMap.get(ZkStateReader.NODE_NAME_PROP);
            if (!liveNodes.contains(node_name)) {
              // node is not live, so this replica is actually down
              replicaMap.put(ZkStateReader.STATE_PROP, ZkStateReader.DOWN);
            }
          }
        }
      }
    }
  }


}

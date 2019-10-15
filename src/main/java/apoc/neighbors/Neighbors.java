package apoc.neighbors;

import apoc.result.ListResult;
import apoc.result.LongResult;
import apoc.result.NodeListResult;
import apoc.result.NodeResult;
import org.neo4j.graphdb.*;
import org.neo4j.internal.helpers.collection.Pair;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static apoc.path.RelationshipTypeAndDirections.parse;

public class Neighbors {

    @Context
    public Transaction tx;

    @Procedure("apoc.neighbors.tohop")
    @Description("apoc.neighbors.tohop(node, rel-direction-pattern, distance) - returns distinct nodes of the given relationships in the pattern up to a certain distance, can use '>' or '<' for all outgoing or incoming relationships")
    public Stream<NodeResult> neighbors(@Name("node") Node node, @Name(value = "types", defaultValue = "") String types, @Name(value="distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();
        if (types==null || types.isEmpty()) return Stream.empty();

        // Initialize bitmaps for iteration
        Roaring64NavigableMap seen = new Roaring64NavigableMap();
        Roaring64NavigableMap nextA = new Roaring64NavigableMap();
        Roaring64NavigableMap nextB = new Roaring64NavigableMap();
        long nodeId = node.getId();
        seen.addLong(nodeId);
        Iterator<Long> iterator;

        // First Hop
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                nextB.addLong(r.getOtherNodeId(nodeId));
            }
        }

        for(int i = 1; i < distance; i++) {
            // next even Hop
            nextB.andNot(seen);
            seen.or(nextB);
            nextA.clear();
            iterator = nextB.iterator();
            while (iterator.hasNext()) {
                nodeId = iterator.next();
                node = tx.getNodeById(nodeId);
                for (Pair<RelationshipType, Direction> pair : parse(types)) {
                    for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                        nextA.add((r.getOtherNodeId(nodeId)));
                    }
                }
            }

            i++;
            if (i < distance) {
                // next odd Hop
                nextA.andNot(seen);
                seen.or(nextA);
                nextB.clear();
                iterator = nextA.iterator();
                while (iterator.hasNext()) {
                    nodeId = iterator.next();
                    node = tx.getNodeById(nodeId);
                    for (Pair<RelationshipType, Direction> pair : parse(types)) {
                        for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                            nextB.add(r.getOtherNodeId(nodeId));
                        }
                    }
                }
            }
        }
        if((distance % 2) == 0) {
            seen.or(nextA);
        } else {
            seen.or(nextB);
        }
        // remove starting node
        seen.removeLong(node.getId());

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(seen.iterator(), Spliterator.SORTED), false)
                .map(x -> new NodeResult(tx.getNodeById(x)));
    }

    @Procedure("apoc.neighbors.tohop.count")
    @Description("apoc.neighbors.tohop.count(node, rel-direction-pattern, distance) - returns distinct count of nodes of the given relationships in the pattern up to a certain distance, can use '>' or '<' for all outgoing or incoming relationships")
    public Stream<LongResult> neighborsCount(@Name("node") Node node, @Name(value = "types", defaultValue = "") String types, @Name(value="distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();
        if (types==null || types.isEmpty()) return Stream.empty();

        // Initialize bitmaps for iteration
        Roaring64NavigableMap seen = new Roaring64NavigableMap();
        Roaring64NavigableMap nextA = new Roaring64NavigableMap();
        Roaring64NavigableMap nextB = new Roaring64NavigableMap();
        long nodeId = node.getId();
        seen.add(nodeId);
        Iterator<Long> iterator;

        // First Hop
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                nextB.add(r.getOtherNodeId(nodeId));
            }
        }

        for(int i = 1; i < distance; i++) {
            // next even Hop
            nextB.andNot(seen);
            seen.or(nextB);
            nextA.clear();
            iterator = nextB.iterator();
            while (iterator.hasNext()) {
                nodeId = iterator.next();
                node = tx.getNodeById(nodeId);
                for (Pair<RelationshipType, Direction> pair : parse(types)) {
                    for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                        nextA.add(r.getOtherNodeId(nodeId));
                    }
                }
            }

            i++;
            if (i < distance) {
                // next odd Hop
                nextA.andNot(seen);
                seen.or(nextA);
                nextB.clear();
                iterator = nextA.iterator();
                while (iterator.hasNext()) {
                    nodeId = iterator.next();
                    node = tx.getNodeById(nodeId);
                    for (Pair<RelationshipType, Direction> pair : parse(types)) {
                        for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                            nextB.add(r.getOtherNodeId(nodeId));
                        }
                    }
                }
            }
        }
        if((distance % 2) == 0) {
            seen.or(nextA);
        } else {
            seen.or(nextB);
        }
        // remove starting node
        seen.removeLong(node.getId());

        return Stream.of(new LongResult(seen.getLongCardinality()));
    }

    @Procedure("apoc.neighbors.byhop")
    @Description("apoc.neighbors.byhop(node, rel-direction-pattern, distance) - returns distinct nodes of the given relationships in the pattern at each distance, can use '>' or '<' for all outgoing or incoming relationships")
    public Stream<NodeListResult> neighborsByHop(@Name("node") Node node, @Name(value = "types", defaultValue = "") String types, @Name(value="distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();
        if (types==null || types.isEmpty()) return Stream.empty();

        // Initialize bitmaps for iteration
        Roaring64NavigableMap[] seen = new Roaring64NavigableMap[distance.intValue()];
        for(int i = 0; i < distance; i++) {
            seen[i] = new Roaring64NavigableMap();
        }
        long nodeId = node.getId();

        Iterator<Long> iterator;

        // First Hop
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                seen[0].add(r.getOtherNodeId(nodeId));
            }
        }

        for(int i = 1; i < distance; i++) {
            iterator = seen[i-1].iterator();
            while (iterator.hasNext()) {
                node = tx.getNodeById(iterator.next());
                for (Pair<RelationshipType, Direction> pair : parse(types)) {
                    for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                        seen[i].add(r.getOtherNodeId(node.getId()));
                    }
                }
            }
            for(int j = 0; j < i; j++){
                seen[i].andNot(seen[j]);
                seen[i].removeLong(nodeId);
            }
        }

        return Arrays.stream(seen).map(x -> new NodeListResult(
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(x.iterator(), Spliterator.SORTED), false)
                        .map(y -> tx.getNodeById((long) y))
                        .collect(Collectors.toList())));
    }

    @Procedure("apoc.neighbors.byhop.count")
    @Description("apoc.neighbors.byhop.count(node, rel-direction-pattern, distance) - returns distinct nodes of the given relationships in the pattern at each distance, can use '>' or '<' for all outgoing or incoming relationships")
    public Stream<ListResult> neighborsByHopCount(@Name("node") Node node, @Name(value = "types", defaultValue = "") String types, @Name(value="distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();
        if (types==null || types.isEmpty()) return Stream.empty();

        // Initialize bitmaps for iteration
        Roaring64NavigableMap[] seen = new Roaring64NavigableMap[distance.intValue()];
        for(int i = 0; i < distance; i++) {
            seen[i] = new Roaring64NavigableMap();
        }
        long nodeId = node.getId();

        Iterator<Long> iterator;

        // First Hop
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                seen[0].add(r.getOtherNodeId(nodeId));
            }
        }

        for(int i = 1; i < distance; i++) {
            iterator = seen[i-1].iterator();
            while (iterator.hasNext()) {
                node = tx.getNodeById(iterator.next());
                for (Pair<RelationshipType, Direction> pair : parse(types)) {
                    for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                        seen[i].add(r.getOtherNodeId(node.getId()));
                    }
                }
            }
            for(int j = 0; j < i; j++){
                seen[i].andNot(seen[j]);
                seen[i].removeLong(nodeId);
            }
        }

        ArrayList counts = new ArrayList<Long>();
        for(int i = 0; i < distance; i++) {
            counts.add(seen[i].getLongCardinality());
        }

        return Stream.of(new ListResult(counts));
    }

    @Procedure("apoc.neighbors.athop")
    @Description("apoc.neighbors.athop(node, rel-direction-pattern, distance) - returns distinct nodes of the given relationships in the pattern at a distance, can use '>' or '<' for all outgoing or incoming relationships")
    public Stream<NodeResult> neighborsAtHop(@Name("node") Node node, @Name(value = "types", defaultValue = "") String types, @Name(value="distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();
        if (types==null || types.isEmpty()) return Stream.empty();

        // Initialize bitmaps for iteration
        Roaring64NavigableMap[] seen = new Roaring64NavigableMap[distance.intValue()];
        for(int i = 0; i < distance; i++) {
            seen[i] = new Roaring64NavigableMap();
        }
        long nodeId = node.getId();

        Iterator<Long> iterator;

        // First Hop
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                seen[0].add(r.getOtherNodeId(nodeId));
            }
        }

        for(int i = 1; i < distance; i++) {
            iterator = seen[i-1].iterator();
            while (iterator.hasNext()) {
                node = tx.getNodeById(iterator.next());
                for (Pair<RelationshipType, Direction> pair : parse(types)) {
                    for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                        seen[i].add(r.getOtherNodeId(node.getId()));
                    }
                }
            }
            for(int j = 0; j < i; j++){
                seen[i].andNot(seen[j]);
                seen[i].removeLong(nodeId);
            }
        }

        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(seen[distance.intValue() - 1].iterator(), Spliterator.SORTED), false).map(y -> new NodeResult(tx.getNodeById(y)));
    }

    @Procedure("apoc.neighbors.athop.count")
    @Description("apoc.neighbors.athop.count(node, rel-direction-pattern, distance) - returns distinct nodes of the given relationships in the pattern at a distance, can use '>' or '<' for all outgoing or incoming relationships")
    public Stream<LongResult> neighborsAtHopCount(@Name("node") Node node, @Name(value = "types", defaultValue = "") String types, @Name(value="distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();
        if (types == null || types.isEmpty()) return Stream.empty();

        // Initialize bitmaps for iteration
        Roaring64NavigableMap[] seen = new Roaring64NavigableMap[distance.intValue()];
        for (int i = 0; i < distance; i++) {
            seen[i] = new Roaring64NavigableMap();
        }
        long nodeId = node.getId();

        Iterator<Long> iterator;

        // First Hop
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                seen[0].add(r.getOtherNodeId(nodeId));
            }
        }

        for (int i = 1; i < distance; i++) {
            iterator = seen[i - 1].iterator();
            while (iterator.hasNext()) {
                node = tx.getNodeById(iterator.next());
                for (Pair<RelationshipType, Direction> pair : parse(types)) {
                    for (Relationship r : node.getRelationships(pair.first(), pair.other())) {
                        seen[i].add(r.getOtherNodeId(node.getId()));
                    }
                }
            }
            for (int j = 0; j < i; j++) {
                seen[i].andNot(seen[j]);
                seen[i].removeLong(nodeId);
            }
        }

        return Stream.of(new LongResult(seen[distance.intValue() - 1].getLongCardinality()));
    }
}
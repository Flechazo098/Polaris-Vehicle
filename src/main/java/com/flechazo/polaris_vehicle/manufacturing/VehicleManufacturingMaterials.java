package com.flechazo.polaris_vehicle.manufacturing;

import com.flechazo.hkt.business.core.Pathway;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VehicleManufacturingMaterials {
    public static Match match(List<ItemStack> stacks, List<SizedIngredient> materials) {
        int stackCount = stacks.size();
        int materialCount = materials.size();
        int source = 0;
        int firstStack = 1;
        int firstMaterial = firstStack + stackCount;
        int sink = firstMaterial + materialCount;
        int nodeCount = sink + 1;
        int[][] capacity = new int[nodeCount][nodeCount];
        int[][] flow = new int[nodeCount][nodeCount];
        int requiredTotal = 0;

        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            ItemStack stack = stacks.get(stackIndex);
            if (stack.isEmpty()) {
                continue;
            }
            int stackNode = firstStack + stackIndex;
            capacity[source][stackNode] = stack.getCount();
            for (int materialIndex = 0; materialIndex < materialCount; materialIndex++) {
                if (materials.get(materialIndex).ingredient().test(stack)) {
                    capacity[stackNode][firstMaterial + materialIndex] = stack.getCount();
                }
            }
        }
        for (int materialIndex = 0; materialIndex < materialCount; materialIndex++) {
            int required = materials.get(materialIndex).count();
            requiredTotal = Math.addExact(requiredTotal, required);
            capacity[firstMaterial + materialIndex][sink] = required;
        }

        int fulfilledTotal = maximumFlow(capacity, flow, source, sink);
        List<Integer> fulfilled = new ArrayList<>(materialCount);
        for (int materialIndex = 0; materialIndex < materialCount; materialIndex++) {
            fulfilled.add(flow[firstMaterial + materialIndex][sink]);
        }
        int[] consumedByStack = new int[stackCount];
        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            int stackNode = firstStack + stackIndex;
            for (int materialIndex = 0; materialIndex < materialCount; materialIndex++) {
                consumedByStack[stackIndex] += flow[stackNode][firstMaterial + materialIndex];
            }
        }
        return new Match(fulfilledTotal == requiredTotal, fulfilled, consumedByStack);
    }

    public static boolean consume(Inventory inventory, List<SizedIngredient> materials) {
        return Pathway.just(match(inventory.items, materials))
                .filter(Match::complete)
                .map(match -> {
                    for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
                        int amount = match.consumedFromStack(index);
                        if (amount <= 0) {
                            continue;
                        }
                        ItemStack stack = inventory.items.get(index);
                        stack.shrink(amount);
                        if (stack.isEmpty()) {
                            inventory.items.set(index, ItemStack.EMPTY);
                        }
                    }
                    inventory.setChanged();
                    return true;
                })
                .getOrElse(false);
    }

    private static int maximumFlow(int[][] capacity, int[][] flow, int source, int sink) {
        int total = 0;
        int[] parent = new int[capacity.length];
        while (findAugmentingPath(capacity, flow, source, sink, parent)) {
            int amount = Integer.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) {
                int previous = parent[node];
                amount = Math.min(amount, capacity[previous][node] - flow[previous][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                int previous = parent[node];
                flow[previous][node] += amount;
                flow[node][previous] -= amount;
            }
            total += amount;
        }
        return total;
    }

    private static boolean findAugmentingPath(
            int[][] capacity,
            int[][] flow,
            int source,
            int sink,
            int[] parent
    ) {
        Arrays.fill(parent, -1);
        parent[source] = source;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next = 0; next < capacity.length; next++) {
                if (parent[next] != -1 || capacity[current][next] - flow[current][next] <= 0) {
                    continue;
                }
                parent[next] = current;
                if (next == sink) {
                    return true;
                }
                queue.addLast(next);
            }
        }
        return false;
    }

    public static final class Match {
        private final boolean complete;
        private final List<Integer> fulfilledCounts;
        private final int[] consumedByStack;

        Match(boolean complete, List<Integer> fulfilledCounts, int[] consumedByStack) {
            this.complete = complete;
            this.fulfilledCounts = List.copyOf(fulfilledCounts);
            this.consumedByStack = consumedByStack.clone();
        }

        public boolean complete() {
            return complete;
        }

        public int fulfilledCount(int materialIndex) {
            return fulfilledCounts.get(materialIndex);
        }

        public List<Integer> fulfilledCounts() {
            return fulfilledCounts;
        }

        public int consumedFromStack(int stackIndex) {
            return consumedByStack[stackIndex];
        }
    }
}

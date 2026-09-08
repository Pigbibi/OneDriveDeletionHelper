package cn.lisiyi.photokeep.core;

import java.util.*;

/** A tentative match is never sufficient to delete. The remote bytes must match SHA-256 first. */
public final class Matcher {
    public List<Binding> propose(List<Media> local, List<CloudItem> remote, Set<String> folders, Collection<Binding> existing) {
        Set<String> usedCloud = new HashSet<>(), usedLocal = new HashSet<>();
        for (Binding b : existing) { usedCloud.add(b.cloudId); usedLocal.add(b.localKey); }
        Map<Long, List<CloudItem>> bySize = new HashMap<>();
        for (CloudItem c : remote) if (!c.folder() && !usedCloud.contains(c.id()))
            bySize.computeIfAbsent(c.size(), x -> new ArrayList<>()).add(c);
        Map<String, List<Media>> byDigest = new HashMap<>();
        for (Media m : local) if (!m.trashed() && !m.pending() && m.readable())
            byDigest.computeIfAbsent(m.sha256(), x -> new ArrayList<>()).add(m);
        List<Binding> proposals = new ArrayList<>();
        Map<String, Integer> claims = new HashMap<>();
        for (Media m : local) {
            if (usedLocal.contains(m.key()) || m.trashed() || m.pending() || !m.readable()
                    || folders.stream().noneMatch(f -> DeletionPolicy.isWithin(m.folder(), f))
                    || byDigest.get(m.sha256()).size() != 1) continue;
            List<CloudItem> candidates = bySize.getOrDefault(m.size(), List.of());
            List<CloudItem> named = candidates.stream().filter(c -> c.name().equalsIgnoreCase(m.name())).collect(java.util.stream.Collectors.toList());
            List<CloudItem> matches = named.isEmpty() ? candidates : named;
            if (matches.size() != 1) continue;
            CloudItem c = matches.get(0);
            proposals.add(new Binding(c.id(), m.key(), m.sha256(), m.size(), m.name(), m.folder(), c.path()));
            claims.merge(c.id(), 1, Integer::sum);
        }
        return proposals.stream().filter(b -> claims.get(b.cloudId) == 1).collect(java.util.stream.Collectors.toList());
    }
}

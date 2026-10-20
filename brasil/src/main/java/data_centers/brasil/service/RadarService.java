package data_centers.brasil.service;

import data_centers.brasil.dto.radar.RadarDTO.*;
import data_centers.brasil.exception.*;
import data_centers.brasil.repository.RadarRepository;
import data_centers.brasil.service.RadarData.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import static data_centers.brasil.service.RadarData.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class RadarService {
    private final RadarRepository repository;
    public record Query(String uf, String region, String operator, String installationType) {}

    private List<Entry> all() { return repository.rows().stream().map(RadarData::entry).toList(); }
    private List<Entry> filter(List<Entry> rows, Query q) {
        String state = q.uf() == null ? null : requireUf(q.uf());
        String region = null;
        if (q.region() != null) {
            region = STATES.values().stream().map(StateInfo::region)
                .filter(r -> Objects.equals(cityKey(r), cityKey(q.region()))).findFirst()
                .orElseThrow(() -> new InvalidFilterException("Região inválida."));
        }
        String selectedRegion = region;
        if (q.operator() != null && clean(q.operator()) == null ||
            q.installationType() != null && clean(q.installationType()) == null)
            throw new InvalidFilterException("Filtro vazio ou não informado.");
        return rows.stream().filter(e -> state == null || state.equals(e.uf()))
            .filter(e -> selectedRegion == null || e.uf() != null && selectedRegion.equals(STATES.get(e.uf()).region()))
            .filter(e -> q.operator() == null || Objects.equals(key(q.operator()), e.operator()))
            .filter(e -> q.installationType() == null || Objects.equals(key(q.installationType()), key(e.raw().type())))
            .toList();
    }
    private long distinct(List<Entry> rows, Function<Entry, String> field) {
        return rows.stream().map(field).filter(Objects::nonNull).distinct().count();
    }
    private BigDecimal sum(List<Entry> rows, Function<Entry, BigDecimal> field) {
        return rows.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }
    private long informed(List<Entry> rows, Function<Entry, BigDecimal> field) {
        return rows.stream().filter(e -> field.apply(e) != null).count();
    }
    private BigDecimal avg(List<Entry> rows, Function<Entry, BigDecimal> field) {
        BigDecimal total = sum(rows, field);
        return total == null ? null : total.divide(BigDecimal.valueOf(informed(rows, field)), 2, RoundingMode.HALF_UP);
    }
    private Metrics metrics(List<Entry> rows) {
        return new Metrics(rows.size(), distinct(rows, Entry::operator), distinct(rows, Entry::cityId),
            distinct(rows, Entry::uf), rows.stream().filter(Entry::geo).count(),
            sum(rows, Entry::networks), avg(rows, Entry::networks), sum(rows, Entry::ix), avg(rows, Entry::ix),
            sum(rows, Entry::carriers), avg(rows, Entry::carriers),
            rows.stream().filter(e -> e.networks() != null || e.ix() != null || e.carriers() != null).count(),
            informed(rows, Entry::networks), informed(rows, Entry::ix), informed(rows, Entry::carriers));
    }
    public Metrics overview(Query q) { return metrics(filter(all(), q)); }
    private Map<String, List<Entry>> groups(List<Entry> rows, Function<Entry, String> field) {
        Map<String, List<Entry>> groups = new TreeMap<>();
        for (Entry e : rows) {
            String k = field.apply(e);
            if (k != null) groups.computeIfAbsent(k, ignored -> new ArrayList<>()).add(e);
        }
        return groups;
    }
    private String display(List<Entry> rows, Function<Entry, String> field) {
        return rows.stream().map(field).filter(s -> clean(s) != null).sorted().findFirst().orElse(null);
    }
    private State state(String uf, List<Entry> rows, long national) {
        Metrics m = metrics(rows);
        BigDecimal share = national == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(rows.size())
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(national), 2, RoundingMode.HALF_UP);
        return new State(uf, STATES.get(uf).name(), m.totalInstalacoes(), m.totalOperadores(), m.totalMunicipios(),
            share, m.totalRedesRegistradas(), m.mediaRedesPorInstalacao(), m.totalIxRegistrados(), m.mediaIxPorInstalacao(),
            m.totalCarriersRegistrados(), m.mediaCarriersPorInstalacao(), m.instalacoesComCoordenadas(),
            m.instalacoesComConectividade(), m.instalacoesComRedes(), m.instalacoesComIx(), m.instalacoesComCarriers());
    }
    private <T> Comparator<T> comparator(String sort, String direction, Map<String, Function<T, ? extends Comparable<?>>> fields,
        Function<T, String> tie) {
        if (!fields.containsKey(sort) || !Set.of("asc", "desc").contains(direction))
            throw new InvalidFilterException("sortBy ou direction inválido.");
        Function<T, ? extends Comparable<?>> field = fields.get(sort);
        // Ausência fica no final em ambas as direções; desempate é estável.
        return (a, b) -> {
            Comparable<?> x = field.apply(a), y = field.apply(b);
            int c;
            if (x == null || y == null) c = x == y ? 0 : x == null ? 1 : -1;
            else { c = compare(x, y); if (direction.equals("desc")) c = -c; }
            return c != 0 ? c : Comparator.nullsLast(String::compareTo).compare(tie.apply(a), tie.apply(b));
        };
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Comparable x, Comparable y) { return x.compareTo(y); }
    public List<State> states(Query q, String sort, String direction) {
        var order = comparator(sort, direction, Map.<String, Function<State, ? extends Comparable<?>>>of(
            "installations", State::totalInstalacoes, "operators", State::totalOperadores,
            "municipalities", State::totalMunicipios, "networks", State::totalRedesRegistradas,
            "averageNetworks", State::mediaRedesPorInstalacao, "ix", State::totalIxRegistrados,
            "carriers", State::totalCarriersRegistrados), State::uf);
        var all = all();
        return groups(filter(all, q), Entry::uf).entrySet().stream()
            .map(e -> state(e.getKey(), e.getValue(), all.size())).sorted(order).toList();
    }
    private Query scoped(String uf, Query q) {
        String state = requireUf(uf);
        if (q.uf() != null && !state.equals(requireUf(q.uf())))
            throw new InvalidFilterException("UF do filtro difere da UF do caminho.");
        return new Query(state, q.region(), q.operator(), q.installationType());
    }
    private List<Entry> requireRows(List<Entry> rows) {
        if (rows.isEmpty()) throw new ResourceNotFoundException("Estado sem registros para os filtros informados.");
        return rows;
    }
    public StateDetail detail(String uf, Query q) {
        Query scope = scoped(uf, q);
        var all = all();
        var rows = requireRows(filter(all, scope));
        State s = state(scope.uf(), rows, all.size());
        return new StateDetail(s.uf(), s.nome(), s.totalInstalacoes(), s.totalOperadores(), s.totalMunicipios(),
            s.participacaoNacional(), s.totalRedesRegistradas(), s.mediaRedesPorInstalacao(), s.totalIxRegistrados(),
            s.mediaIxPorInstalacao(), s.totalCarriersRegistrados(), s.mediaCarriersPorInstalacao(),
            s.instalacoesComCoordenadas(), s.instalacoesComConectividade(), s.instalacoesComRedes(),
            s.instalacoesComIx(), s.instalacoesComCarriers(), operators(rows, 10), cities(rows, "installations", "desc", 10));
    }
    public List<City> stateCities(String uf, Query q, String sort, String direction) {
        return cities(requireRows(filter(all(), scoped(uf, q))), sort, direction, Integer.MAX_VALUE);
    }
    private int limit(int limit) {
        if (limit < 1 || limit > 1000) throw new InvalidFilterException("limit deve estar entre 1 e 1000.");
        return limit;
    }
    public List<City> cityRanking(Query q, int limit, String sort, String direction) {
        limit(limit);
        return cities(filter(all(), q), sort, direction, limit);
    }
    private List<City> cities(List<Entry> rows, String sort, String direction, int limit) {
        var order = comparator(sort, direction, Map.<String, Function<City, ? extends Comparable<?>>>of(
            "installations", City::totalInstalacoes, "operators", City::totalOperadores,
            "networks", City::totalRedesRegistradas, "averageNetworks", City::mediaRedesPorInstalacao,
            "ix", City::totalIxRegistrados, "carriers", City::totalCarriersRegistrados),
            c -> c.uf() + ":" + Objects.toString(c.codigoIbge(), c.municipio()));
        return groups(rows, Entry::cityId).values().stream().map(g -> {
            Entry e = g.getFirst();
            return new City(display(g, x -> x.raw().city()), e.uf(), e.ibge(), g.size(), distinct(g, Entry::operator),
                sum(g, Entry::networks), avg(g, Entry::networks), sum(g, Entry::ix), sum(g, Entry::carriers));
        }).sorted(order).limit(limit).toList();
    }
    public List<Operator> operatorRanking(Query q, int limit) {
        return operators(filter(all(), q), limit(limit));
    }
    private List<Operator> operators(List<Entry> rows, int limit) {
        return groups(rows, Entry::operator).values().stream().map(g -> new Operator(
            display(g, e -> e.raw().operator()), g.size(), distinct(g, Entry::uf), distinct(g, Entry::cityId), sum(g, Entry::networks)))
            .sorted(Comparator.comparingLong(Operator::totalInstalacoes).reversed().thenComparing(Operator::operador))
            .limit(limit).toList();
    }
    public List<MapPoint> map(Query q) {
        return filter(all(), q).stream().filter(Entry::geo).map(e -> new MapPoint(e.raw().id(), e.raw().operator(),
            e.raw().unit(), e.raw().city(), e.uf(), e.latitude(), e.longitude(), e.raw().precision(), e.raw().type(),
            e.networks(), e.ix(), e.carriers()))
            .sorted(Comparator.comparing(MapPoint::uf, Comparator.nullsLast(String::compareTo))
                .thenComparing(MapPoint::id, Comparator.nullsLast(String::compareTo))
                .thenComparing(MapPoint::unidade, Comparator.nullsLast(String::compareTo))).toList();
    }
    public Filters filters(Query q) {
        var rows = filter(all(), q);
        var states = rows.stream().map(Entry::uf).filter(Objects::nonNull).distinct().sorted().toList();
        return new Filters(states, options(rows, e -> e.raw().operator()), options(rows, e -> e.raw().type()),
            states.stream().map(s -> STATES.get(s).region()).distinct().sorted().toList());
    }
    private List<String> options(List<Entry> rows, Function<Entry, String> field) {
        return groups(rows, e -> key(field.apply(e))).values().stream().map(g -> display(g, field)).sorted().toList();
    }
}

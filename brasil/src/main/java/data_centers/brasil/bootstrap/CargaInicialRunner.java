package data_centers.brasil.bootstrap;

import data_centers.brasil.service.PopulacaoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("popular")
@RequiredArgsConstructor
public class CargaInicialRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CargaInicialRunner.class);
    private final PopulacaoService service;
    private final ConfigurableApplicationContext context;
    @Value("${app.carga.encerrar:false}")
    private boolean encerrar;

    @Override
    public void run(ApplicationArguments args) {
        var result = service.populateClasspath();
        log.info("CARGA CONCLUÍDA: linhas={}, dataCentersCriados={}, reutilizados={}, fontes={}, indicadores={}, conteúdos={}, linhasBrutas={}, ignorados={}, avisos={}",
            result.totalLinhas(), result.dataCentersCriados(), result.dataCentersReutilizados(),
            result.fontesCriadas(), result.indicadoresCriados(), result.conteudosCriados(),
            result.linhasBrutasCriadas(), result.registrosIgnorados(), result.avisos().size());
        if (encerrar) context.close();
    }
}

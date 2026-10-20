package data_centers.brasil.mapper;
import data_centers.brasil.dto.ConteudoPesquisaResponse;
import data_centers.brasil.model.ConteudoPesquisa;
public final class ConteudoPesquisaMapper {
    private ConteudoPesquisaMapper() {}
    public static ConteudoPesquisaResponse toResponse(ConteudoPesquisa entity) {
        return new ConteudoPesquisaResponse(entity.getId(), entity.getDataCenter() == null ? null : entity.getDataCenter().getId(), entity.getTitulo(), entity.getConteudo(), entity.getCategoria(), entity.getFonte() == null ? null : entity.getFonte().getId(), entity.getDataPublicacao());
    }
}

/** Contratos da API Data Centers Brasil. Copie para o frontend; não contém credenciais. */

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface PageResponse<T> {
  content: T[];
  page: PageMetadata;
}

export interface DataCenterRequest {
  nome: string;
  empresa?: string | null;
  cidade: string;
  estado: string;
  pais?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  descricao?: string | null;
  potenciaMw?: number | null;
  areaM2?: number | null;
  tipoInstalacao?: string | null;
  statusValidacao?: string | null;
  dataValidacao?: string | null;
}

export interface DataCenterResponse {
  id: number;
  nome: string;
  empresa: string | null;
  cidade: string;
  estado: string;
  pais: string | null;
  latitude: number | null;
  longitude: number | null;
  descricao: string | null;
  potenciaMw: number | null;
  areaM2: number | null;
  tipoInstalacao: string | null;
  statusValidacao: string | null;
  dataValidacao: string | null;
}

export interface FonteRequest {
  instituicao: string;
  titulo: string;
  url?: string | null;
  dataPublicacao?: string | null;
  dataConsulta: string;
}

export interface FonteResponse {
  id: number;
  instituicao: string;
  titulo: string;
  url: string | null;
  dataPublicacao: string | null;
  dataConsulta: string;
}

export interface IndicadorAmbientalRequest {
  dataCenterId: number;
  tipo: string;
  valor?: number | null;
  unidade?: string | null;
  periodo?: string | null;
  fonteId?: number | null;
  observacoes?: string | null;
}

export interface IndicadorAmbientalResponse {
  id: number;
  dataCenterId: number;
  tipo: string;
  valor: number | null;
  unidade: string | null;
  periodo: string | null;
  fonteId: number | null;
  observacoes: string | null;
}

export interface BeneficioRequest {
  dataCenterId: number;
  tipo: string;
  descricao: string;
  fonteId?: number | null;
}

export interface BeneficioResponse {
  id: number;
  dataCenterId: number;
  tipo: string;
  descricao: string;
  fonteId: number | null;
}

export interface ConteudoPesquisaRequest {
  dataCenterId?: number | null;
  titulo: string;
  conteudo: string;
  categoria?: string | null;
  fonteId: number;
  dataPublicacao?: string | null;
}

export interface ConteudoPesquisaResponse {
  id: number;
  dataCenterId: number | null;
  titulo: string;
  conteudo: string;
  categoria: string | null;
  fonteId: number;
  dataPublicacao: string | null;
}

export interface MapaResponse {
  id: number;
  nome: string;
  empresa: string | null;
  cidade: string;
  estado: string;
  latitude: number;
  longitude: number;
  potenciaMw: number | null;
  tipoInstalacao: string | null;
}

export interface DashboardResponse {
  cidade: string;
  quantidadeDataCenters: number;
  potenciaTotalMw: number;
}

export interface DataCenterImportacao {
  idOriginal: string | null;
  operador: string | null;
  unidade: string | null;
  municipio: string | null;
  uf: string | null;
  codigoIbge: string | null;
  endereco: string | null;
  latitude: string | null;
  longitude: string | null;
  precisaoGeo: string | null;
  tipoInstalacao: string | null;
  statusOperacional: string | null;
  inicioOperacao: string | null;
  potenciaTiMwUnidade: string | null;
  potenciaCampusMw: string | null;
  areaM2Unidade: string | null;
  redesConectadas: string | null;
  ixConectados: string | null;
  carriers: string | null;
  voltadaIa: string | null;
  resfriamento: string | null;
  fonteEnergia: string | null;
  pueDeclarado: string | null;
  wueDeclarado: string | null;
  certificacoes: string | null;
  robustezEvidencia: string | null;
  fonteUrl: string | null;
  dataLevantamento: string | null;
}

export interface ImportacaoResponse {
  linhasImportadas: number;
}

export interface CargaAviso {
  idOriginal: string;
  campo: string;
  valorOriginal: string;
  mensagem: string;
}

export interface CargaPreviaResponse {
  arquivo: string;
  totalLinhas: number;
  registrosComCoordenadas: number;
  registrosComPotenciaNumerica: number;
  amostra: DataCenterRequest[];
  avisos: CargaAviso[];
}

export interface CargaResponse {
  arquivo: string;
  totalLinhas: number;
  dataCentersCriados: number;
  dataCentersReutilizados: number;
  fontesCriadas: number;
  indicadoresCriados: number;
  conteudosCriados: number;
  linhasBrutasCriadas: number;
  registrosIgnorados: number;
  avisos: CargaAviso[];
}

/** Aceita Problem Detail e o erro padrão do Spring. Um erro HTTP também pode não ter corpo. */
export interface ApiErrorBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Array<{ field: string; message: string }>;
  timestamp?: string;
  error?: string;
  message?: string;
  path?: string;
  trace?: string;
}

// Filtros de query: valores vazios são ignorados; filtros desconhecidos retornam 400.
export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string | readonly string[];
}

type ScalarKeys<T> = Extract<keyof T, string>;
type DateField = "dataValidacao" | "dataPublicacao" | "dataConsulta";
type RangeKeys<T> = {
  [K in ScalarKeys<T>]: NonNullable<T[K]> extends number ? K : K extends DateField ? K : never
}[ScalarKeys<T>];

export type FieldFilters<T> = {
  [K in ScalarKeys<T>]?: NonNullable<T[K]>;
} & {
  [K in ScalarKeys<T> as `${K}Eq`]?: NonNullable<T[K]>;
} & {
  [K in ScalarKeys<T> as `${K}In`]?: string | readonly NonNullable<T[K]>[];
} & {
  [K in ScalarKeys<T> as `${K}IsNull`]?: boolean;
} & {
  [K in RangeKeys<T> as `${K}Min`]?: NonNullable<T[K]>;
} & {
  [K in RangeKeys<T> as `${K}Max`]?: NonNullable<T[K]>;
};

export type DataCenterFilters = FieldFilters<DataCenterResponse> & PageQuery & {
  cidades?: string | readonly string[];
};
export type FonteFilters = FieldFilters<FonteResponse> & PageQuery;
export type IndicadorAmbientalFilters = FieldFilters<IndicadorAmbientalResponse> & PageQuery;
export type BeneficioFilters = FieldFilters<BeneficioResponse> & PageQuery;
export type ConteudoPesquisaFilters = FieldFilters<ConteudoPesquisaResponse> & PageQuery;
export type ImportacaoFilters = FieldFilters<DataCenterImportacao> & Pick<PageQuery, "page" | "size">;
export type DashboardFilters = FieldFilters<DataCenterResponse> & FieldFilters<DashboardResponse> & {
  cidades?: string | readonly string[];
};

/** Codifica Eq/Min/Max/IsNull e listas In/cidades. Para sort múltiplo, repete a chave. */
export function toQueryString(filters: object): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      if (key === "sort") value.forEach(item => query.append(key, String(item)));
      else if (value.length) query.set(key, value.map(String).join(","));
    } else query.set(key, String(value));
  }
  return query.toString();
}

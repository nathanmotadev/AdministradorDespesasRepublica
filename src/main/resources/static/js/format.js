// Formatação de dinheiro e datas (sempre em pt-BR, sem depender do fuso horário).

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export const brl = (valor) => moeda.format(valor);

/** Converte o texto digitado ("50,90", "1.234,56", "50.9") em número, ou NaN se inválido. */
export function lerValor(texto) {
    const limpo = String(texto).trim().replace(/[R$\s]/g, '');
    if (!limpo) return NaN;
    const normalizado = limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo;
    return /^\d+(\.\d+)?$/.test(normalizado) ? Number(normalizado) : NaN;
}

/** Mesma regra do back-end: divide em centavos e os centavos que sobram vão para os primeiros. */
export function dividirEmCentavos(centavos, partes) {
    const base = Math.floor(centavos / partes);
    const resto = centavos % partes;
    return Array.from({ length: partes }, (_, i) => base + (i < resto ? 1 : 0));
}

export function paraISO(data) {
    const mm = String(data.getMonth() + 1).padStart(2, '0');
    const dd = String(data.getDate()).padStart(2, '0');
    return `${data.getFullYear()}-${mm}-${dd}`;
}

export const hojeISO = () => paraISO(new Date());

export function somarDias(dias) {
    const data = new Date();
    data.setDate(data.getDate() + dias);
    return paraISO(data);
}

/** "2026-10-02" -> "02/10/2026" */
export function dataBR(iso, comAno = true) {
    if (!iso) return '';
    const [ano, mes, dia] = iso.split('-');
    return comAno ? `${dia}/${mes}/${ano}` : `${dia}/${mes}`;
}

export const mesAtual = () => hojeISO().slice(0, 7);

export function deslocarMes(mes, delta) {
    const [ano, m] = mes.split('-').map(Number);
    const data = new Date(ano, m - 1 + delta, 1);
    return paraISO(data).slice(0, 7);
}

export function rotuloMes(mes) {
    const [ano, m] = mes.split('-').map(Number);
    return new Date(ano, m - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
}

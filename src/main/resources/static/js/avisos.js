// Sininho do topo: reúne o que pede atenção agora, calculado só com os dados que a tela já tem.
import { abrirModal, h } from './ui.js';
import { icone } from './components.js';
import { brl, dataBR, hojeISO } from './format.js';

const DIAS_DE_ANTECEDENCIA = 3;

const diasAte = (iso) => Math.round((new Date(`${iso}T00:00:00`) - new Date(`${hojeISO()}T00:00:00`)) / 86400000);
const quando = (dias) => (dias < 0 ? 'atrasada' : dias === 0 ? 'vence hoje' : dias === 1 ? 'vence amanhã' : `vence em ${dias} dias`);

/** Dívidas que vencem logo (ou já venceram) e contas fixas chegando, da mais urgente para a menos. */
export function calcularAvisos(state) {
    const avisos = [];
    for (const d of state.dividas?.euDevo ?? []) {
        if (!d.vencimento) continue;
        const dias = diasAte(d.vencimento);
        if (dias <= DIAS_DE_ANTECEDENCIA) {
            avisos.push({ dias, urgente: dias < 0, titulo: `Você deve ${brl(d.valor)} a ${d.pessoa.nome}`,
                detalhe: `${d.descricao} · ${quando(dias)} (${dataBR(d.vencimento, false)})` });
        }
    }
    for (const d of state.dividas?.meDevem ?? []) {
        if (d.atrasada) {
            avisos.push({ dias: -1, urgente: true, titulo: `${d.pessoa.nome} está com ${brl(d.valor)} em atraso`,
                detalhe: d.descricao });
        }
    }
    for (const c of state.contasFixas ?? []) {
        if (!c.ativa) continue;
        if (c.aguardandoValor) {
            if (c.pagador.id === state.eu.id) {
                avisos.push({ dias: -1, urgente: true, titulo: `Informe o valor de ${c.descricao}`,
                    detalhe: `A fatura venceu em ${dataBR(c.proximoVencimento, false)} · Contas fixas` });
            }
            continue;
        }
        const dias = diasAte(c.proximoVencimento);
        if (dias >= 0 && dias <= DIAS_DE_ANTECEDENCIA) {
            avisos.push({ dias, urgente: false, titulo: `${c.descricao} ${quando(dias)}`,
                detalhe: `Conta fixa · ${dataBR(c.proximoVencimento, false)}` });
        }
    }
    return avisos.sort((a, b) => a.dias - b.dias);
}

function abrirAvisos(state) {
    const avisos = calcularAvisos(state);
    let fechar;
    const corpo = avisos.length === 0
        ? h('p', { class: 'muted' }, 'Sem avisos por enquanto. Quando algo vencer ou atrasar, aparece aqui.')
        : h('ul', { class: 'avisos-lista' }, avisos.map((a) => h('li', { class: a.urgente ? 'urgente' : '' },
            h('strong', {}, a.titulo), h('span', { class: 'muted' }, a.detalhe))));
    fechar = abrirModal({
        titulo: 'Avisos', corpo,
        rodape: [h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Fechar')],
    });
}

export function botaoAvisos(state) {
    return h('button', { class: 'icon-btn redondo botao-avisos', type: 'button', onclick: () => abrirAvisos(state) },
        icone('sino', 20), h('span', { class: 'ponto-aviso', hidden: true }));
}

/** Acende ou apaga o pontinho laranja e atualiza o texto lido por leitores de tela. */
export function atualizarAvisos(state) {
    const total = calcularAvisos(state).length;
    document.querySelectorAll('.botao-avisos').forEach((botao) => {
        botao.setAttribute('aria-label', total === 0 ? 'Avisos' : `Avisos, ${total} ${total === 1 ? 'novo' : 'novos'}`);
        botao.querySelector('.ponto-aviso').hidden = total === 0;
    });
}

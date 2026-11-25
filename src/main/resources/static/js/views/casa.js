import { api } from '../api.js';
import { abrirModal, h, toast } from '../ui.js';
import { avatar, executar, icone, imagemMoradia } from '../components.js';
import { definirTema, preferenciaTema } from '../tema.js';
import { viewMoradores } from './moradores.js';
import { cartaoContasFixas } from './contas-fixas.js';

/** Aba "Casa": nome, convite (administrador), moradores e a conta de quem está logado. */
export function viewCasa(ctx) {
    const { state } = ctx;
    return h('div', { class: 'stack' },
        h('h1', {}, 'Casa'),
        cartaoCasa(ctx),
        state.eu.admin && cartaoConvite(ctx),
        h('div', { class: 'so-celular' }, cartaoContasFixas(ctx)),
        viewMoradores(ctx),
        cartaoAparencia(),
        cartaoConta(ctx),
        state.eu.admin && cartaoExcluir(ctx));
}

const MORADIAS = [
    { tipo: 'CASA', titulo: 'Casa', sub: 'sobrado, casa de vila' },
    { tipo: 'APARTAMENTO', titulo: 'Apartamento', sub: 'prédio, condomínio' },
];

function cartaoCasa({ state, recarregar }) {
    function abrirEdicao() {
        let tipo = state.casa.tipo;
        const campo = h('input', { type: 'text', maxlength: 80, value: state.casa.nome, id: 'campo-nome-casa' });
        const opcoes = MORADIAS.map((m) => h('label', { class: 'opcao-moradia' },
            h('input', { type: 'radio', name: 'tipo-casa', value: m.tipo, checked: m.tipo === tipo,
                onchange: () => { tipo = m.tipo; } }),
            imagemMoradia(m.tipo, '', true),
            h('span', { class: 'opcao-titulo' }, m.titulo),
            h('span', { class: 'opcao-sub' }, m.sub)));

        const enviar = async (evento) => {
            evento.preventDefault();
            const nome = campo.value.trim();
            if (!nome) return;
            const ok = await executar(() => api.editarCasa(state.casa.id, nome, tipo), 'Casa atualizada');
            if (ok) { fechar(); await recarregar(); }
        };
        const fechar = abrirModal({
            titulo: 'Editar casa',
            corpo: h('form', { onsubmit: enviar },
                h('div', { class: 'field' }, h('label', { for: 'campo-nome-casa' }, 'Nome da casa'), campo),
                h('fieldset', { class: 'field escolha-moradia' },
                    h('legend', { class: 'label' }, 'Vocês moram em…'),
                    h('div', { class: 'opcoes-moradia' }, opcoes))),
            rodape: [
                h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'),
                h('button', { class: 'btn primary', type: 'button', onclick: enviar }, 'Salvar'),
            ],
        });
    }

    const tipo = MORADIAS.find((m) => m.tipo === state.casa.tipo) ?? MORADIAS[0];
    return h('section', { class: 'card cartao-casa' },
        imagemMoradia(state.casa.tipo, 'cartao-casa-imagem', true),
        h('div', { class: 'cartao-casa-texto' },
            h('div', { class: 'dica' }, tipo.titulo),
            h('h2', {}, state.casa.nome),
            state.eu.admin && h('button', { class: 'btn small', type: 'button', onclick: abrirEdicao }, 'Editar')));
}

/** Tema do app neste aparelho: automático (segue o sistema), claro ou escuro. */
function cartaoAparencia() {
    const atual = preferenciaTema();
    const opcao = (valor, rotulo) => h('button', { type: 'button', role: 'radio',
        'aria-checked': String(atual === valor), onclick: () => definirTema(valor) }, rotulo);
    return h('section', { class: 'card', 'aria-labelledby': 'titulo-aparencia' },
        h('div', { class: 'card-head' }, h('h2', { id: 'titulo-aparencia' }, 'Aparência')),
        h('div', { class: 'segmentado', role: 'radiogroup', 'aria-labelledby': 'titulo-aparencia' },
            opcao('auto', 'Automático'), opcao('claro', 'Claro'), opcao('escuro', 'Escuro')),
        h('p', { class: 'dica', style: 'margin-top:.6rem' }, 'Vale só para este aparelho. No automático, segue o tema do sistema.'));
}

function cartaoConvite({ state }) {
    const codigo = h('div', { class: 'codigo num', 'aria-live': 'polite' }, state.codigoConvite ?? '…');
    let texto = '';

    api.convite(state.casa.id)
        .then((resposta) => {
            if (!state.casa) return; // saiu da conta antes de a resposta chegar
            state.codigoConvite = resposta.codigo;
            codigo.textContent = resposta.codigo;
            texto = `Entra na "${state.casa.nome}" no república: abra ${location.origin}, toque em "Tenho um código de convite" `
                + `e use o código ${resposta.codigo}.`;
        })
        .catch((erro) => { codigo.textContent = '—'; toast(erro.message, 'erro'); });

    async function copiar() {
        if (!texto) return;
        try {
            await navigator.clipboard.writeText(texto);
            toast('Convite copiado. Agora é só mandar no WhatsApp');
        } catch {
            toast('Não consegui copiar. Selecione o código e copie manualmente', 'erro');
        }
    }

    return h('section', { class: 'card convite', 'aria-labelledby': 'titulo-convite' },
        h('h2', { id: 'titulo-convite' }, 'Convidar alguém'),
        h('p', { class: 'muted' }, 'Quem tiver este código cria a conta e entra na sua casa.'),
        codigo,
        h('button', { class: 'btn primary', type: 'button', onclick: copiar }, icone('copiar', 18), 'Copiar convite'));
}

function cartaoConta({ state, sair }) {
    return h('section', { class: 'card' },
        h('div', { class: 'morador' },
            avatar(state.eu),
            h('div', { class: 'nome' }, state.eu.nome, h('div', { class: 'dica' },
                state.eu.admin ? 'Administrador da casa' : 'Morador')),
            h('button', { class: 'btn', type: 'button', onclick: sair }, 'Sair')));
}

/** Zona de perigo (só administrador): excluir a casa exige digitar o nome dela. */
function cartaoExcluir({ state, sair }) {
    function abrir() {
        const erro = h('p', { class: 'field-error', role: 'alert' });
        const campo = h('input', { type: 'text', autocomplete: 'off', placeholder: state.casa.nome,
            'aria-label': 'Nome da casa para confirmar' });
        const botao = h('button', { class: 'btn danger', type: 'button', disabled: true, onclick: enviar }, 'Excluir para sempre');
        const confere = () => campo.value.trim().toLowerCase() === state.casa.nome.trim().toLowerCase();
        campo.addEventListener('input', () => { botao.disabled = !confere(); });

        async function enviar() {
            if (!confere()) return;
            botao.disabled = true;
            try {
                await api.excluirCasa(state.casa.id, campo.value.trim());
                fechar();
                toast('Casa excluída');
                sair();
            } catch (e) {
                erro.textContent = e.message;
                botao.disabled = false;
            }
        }

        const fechar = abrirModal({
            titulo: 'Excluir a casa?',
            corpo: h('div', {},
                h('p', {}, 'Isso apaga a casa, os moradores, as despesas, as contas fixas e os logins. Não dá para desfazer.'),
                h('p', { class: 'dica', style: 'margin:.5rem 0 .9rem' },
                    'Só é possível quando ninguém tem valores a pagar ou a receber.'),
                h('div', { class: 'field' }, h('label', {}, `Digite "${state.casa.nome}" para confirmar`), campo),
                erro),
            rodape: [h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'), botao],
        });
    }

    return h('section', { class: 'card zona-perigo', 'aria-labelledby': 'titulo-perigo' },
        h('h2', { id: 'titulo-perigo' }, 'Zona de perigo'),
        h('p', { class: 'muted' }, 'Excluir a casa apaga todos os dados dela.'),
        h('button', { class: 'btn danger', type: 'button', onclick: abrir }, 'Excluir casa'));
}

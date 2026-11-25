import { api } from '../api.js';
import { h } from '../ui.js';
import { imagemMoradia, logo } from '../components.js';
import { moradiaLembrada } from '../abertura.js';
import { trocar } from '../movimento.js';

const CAMPO = {
    email: { name: 'email', label: 'E-mail', type: 'email', placeholder: 'voce@email.com', autocomplete: 'email' },
    senha: { name: 'senha', label: 'Senha', type: 'password', autocomplete: 'current-password' },
    novaSenha: { name: 'senha', label: 'Crie uma senha', type: 'password', placeholder: 'Mínimo de 8 caracteres',
        autocomplete: 'new-password' },
    nome: { name: 'nome', label: 'Seu nome na casa', type: 'text', placeholder: 'Como os outros te chamam',
        autocomplete: 'name', maxlength: 80 },
};

/** As três telas de entrada. Cada modo diz o que pedir e qual chamada da API fazer. */
const MODOS = {
    entrar: {
        titulo: ['Bem-vindo', 'de volta'],
        texto: 'Entre para ver o que você deve e o que tem a receber na sua casa.',
        campos: [CAMPO.email, CAMPO.senha],
        botao: 'Entrar',
        enviar: (v) => api.login(v.email, v.senha),
    },
    convite: {
        titulo: ['Entre na', 'sua casa'],
        texto: 'Use o código que o morador da sua casa te enviou. Você só vai ver as dívidas em que participa.',
        campos: [
            { name: 'codigoConvite', label: 'Código de convite', type: 'text', placeholder: 'CASA-XXXXXX',
                autocomplete: 'off', classe: 'codigo-input', maxlength: 20 },
            CAMPO.nome, CAMPO.email, CAMPO.novaSenha],
        botao: 'Criar conta e entrar',
        enviar: (v) => api.cadastrarConvite(v),
    },
    casa: {
        titulo: ['Vamos criar a', 'sua casa'],
        texto: 'Você vira o administrador e convida quem mora com você por um código.',
        escolheMoradia: true,
        campos: [
            { name: 'nomeCasa', label: 'Nome da casa', type: 'text', placeholder: 'Ex.: Casa da Vila',
                autocomplete: 'off', maxlength: 80 },
            CAMPO.nome, CAMPO.email, CAMPO.novaSenha],
        botao: 'Criar casa e entrar',
        enviar: (v) => api.cadastrarCasa(v),
    },
};

export function renderAuth({ topbar, app, nav, aoEntrar }) {
    topbar.replaceChildren();
    nav.replaceChildren();
    app.className = 'container';
    desenhar('entrar');

    function desenhar(modo) {
        app.replaceChildren(tela(modo));
        app.querySelector('input')?.focus({ preventScroll: true });
    }

    function mudarPara(modo) {
        trocar(app, () => { desenhar(modo); window.scrollTo({ top: 0, behavior: 'instant' }); }, modo === 'entrar' ? -1 : 1);
    }

    function tela(modo) {
        const config = MODOS[modo];
        const erro = h('p', { class: 'field-error', role: 'alert' });
        const botao = h('button', { class: 'btn primary block', type: 'submit' }, config.botao);

        let tipo = 'CASA';
        const entradas = config.campos.map((campo) => h('input', {
            type: campo.type, name: campo.name, id: `campo-${campo.name}`, placeholder: campo.placeholder,
            autocomplete: campo.autocomplete, maxlength: campo.maxlength, class: campo.classe, required: true,
        }));

        async function enviar(evento) {
            evento.preventDefault();
            erro.textContent = '';
            botao.disabled = true;
            const valores = Object.fromEntries(entradas.map((e) => [e.name, e.value.trim()]));
            if (config.escolheMoradia) valores.tipo = tipo;
            // a senha vai exatamente como foi digitada (sem "aparar" espaços)
            const campoSenha = entradas.find((e) => e.name === 'senha');
            if (campoSenha) valores.senha = campoSenha.value;
            try {
                const resposta = await config.enviar(valores);
                await aoEntrar(resposta);
            } catch (e) {
                const detalhes = Object.values(e.campos ?? {});
                erro.textContent = detalhes.length ? detalhes.join(' · ') : e.message;
                botao.disabled = false;
            }
        }

        const escolha = config.escolheMoradia && escolhaDeMoradia((novo) => { tipo = novo; });
        const [leve, forte] = config.titulo;

        return h('div', { class: `auth auth-${modo}` },
            logo(),
            !config.escolheMoradia && imagemMoradia(moradiaLembrada(), 'auth-ilustracao', true),
            h('div', {}, h('h1', { class: 'titulo-duplo' }, `${leve} `, h('strong', {}, forte)),
                h('p', { class: 'lead' }, config.texto)),
            h('form', { onsubmit: enviar },
                config.campos.map((campo, i) => [
                    h('div', { class: 'field' }, h('label', { for: `campo-${campo.name}` }, campo.label), entradas[i]),
                    campo.name === 'nomeCasa' ? escolha : null]),
                erro, botao),
            h('div', { class: 'troca' }, ...trocas(modo)),
            h('p', { class: 'auth-slogan' }, 'Contas da casa, sem climão.'));
    }

    /** "Vocês moram em…": casa ou apartamento. Só muda o desenho que aparece no app. */
    function escolhaDeMoradia(aoEscolher) {
        const opcao = (valor, titulo, sub) => h('label', { class: 'opcao-moradia' },
            h('input', { type: 'radio', name: 'tipo', value: valor, checked: valor === 'CASA',
                onchange: () => aoEscolher(valor) }),
            imagemMoradia(valor, '', true),
            h('span', { class: 'opcao-titulo' }, titulo),
            h('span', { class: 'opcao-sub' }, sub));
        return h('fieldset', { class: 'field escolha-moradia' },
            h('legend', { class: 'label' }, 'Vocês moram em…'),
            h('div', { class: 'opcoes-moradia' },
                opcao('CASA', 'Casa', 'sobrado, casa de vila'),
                opcao('APARTAMENTO', 'Apartamento', 'prédio, condomínio')));
    }

    function trocas(modo) {
        const botao = (rotulo, destino, classe = 'btn') =>
            h('button', { class: classe, type: 'button', onclick: () => mudarPara(destino) }, rotulo);
        if (modo === 'entrar') {
            return [botao('Tenho um código de convite', 'convite'), botao('Abrir uma casa nova', 'casa', 'btn ghost')];
        }
        return [botao('Já tenho conta', 'entrar', 'btn ghost')];
    }
}

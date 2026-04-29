const button = document.getElementById('themePulse');
button.addEventListener('click', () => {
  document.body.classList.toggle('alt');
  button.textContent = document.body.classList.contains('alt')
    ? 'Вернуть базовую галактику'
    : 'Пульс Вселенной';
});

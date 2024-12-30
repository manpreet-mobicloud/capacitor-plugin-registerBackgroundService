import { BackgroundService } from 'capacitor-plugin-backgroundservice';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    BackgroundService.echo({ value: inputValue })
}

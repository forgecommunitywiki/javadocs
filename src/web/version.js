
var version_picker;

window.onload = () =>{

    version_picker = document.getElementById("version_picker");

    version_picker.onchange = on_switch;

}

function on_switch(e) {

    var selected = e.srcElement.value
    var url = window.location.href,
        new_url = make_url(url, selected);
    if (new_url != url) {
      window.location.href = new_url;
    }
}

function make_url(url, new_version) {
        return url.replace(/(javadocs[\/]*.*?\/)([1-9.]*?)(\/)/, "$1"+new_version+"$3");
}
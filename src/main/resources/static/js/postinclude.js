const translationHindiForms = document.getElementsByName("translationHindiForm");
translationHindiForms.forEach(addHindiTranslationFormEventListener);
function addHindiTranslationFormEventListener(translationHindiForm) {
    translationHindiForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const url = event.target.action
        console.log("URL: ", url)
        const textarea = event.target.querySelector("textarea")
        const id = textarea.id
        console.log(textarea.value)
        console.log(id)
        const formData = new FormData();
        formData.set("translation", textarea.value);
        formData.set("lang", "hi-IN");
        console.log("formdata: ", formData);
        fetch(url, {
            method: "POST",
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error("Network response was not ok");
                }
                return response;
            })
            .then(data => {
                // Handle the response data here (if needed)
                console.log("POST request successful:", data);
                Toastify({
                    text: `Hindi Translation Saved:\n${id}`,
                    style: {
                        background: "green",
                    },
                    duration: 3000
                }).showToast();
            })
            .catch(error => {
                console.error("Error sending POST request:", error);
                Toastify({
                    text: "Failed to save Hindi Translation!\nCopy the text, refresh this page, and try again.",
                    close: true,
                    style: {
                        background: "red",
                    },
                    duration: -1,
                }).showToast();
            });
    });
}
const translationMarathiForms = document.getElementsByName("translationMarathiForm");
translationMarathiForms.forEach(addMarathiTranslationFormEventListener);
function addMarathiTranslationFormEventListener(translationMarathiForm) {
    translationMarathiForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const url = event.target.action
        console.log("URL: ", url)
        const textarea = event.target.querySelector("textarea")
        const id = textarea.id
        console.log(textarea.value)
        console.log(id)
        const formData = new FormData();
        formData.set("translation", textarea.value);
        formData.set("lang", "mr-IN");
        console.log("formdata: ", formData);
        fetch(url, {
            method: "POST",
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error("Network response was not ok");
                }
                return response;
            })
            .then(data => {
                // Handle the response data here (if needed)
                console.log("POST request successful:", data);
                Toastify({
                    text: `Marathi Translation Saved:\n${id}`,
                    style: {
                        background: "green",
                    },
                    duration: 3000
                }).showToast();
            })
            .catch(error => {
                console.error("Error sending POST request:", error);
                Toastify({
                    text: "Failed to save Marathi Translation!\nCopy the text, refresh this page, and try again.",
                    close: true,
                    style: {
                        background: "red",
                    },
                    duration: -1,
                }).showToast();
            });
    });
}
const translationEnglishForms = document.getElementsByName("translationEnglishForm");
translationEnglishForms.forEach(addEnglishTranslationFormEventListener);
function addEnglishTranslationFormEventListener(translationEnglishForm) {
    translationEnglishForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const url = event.target.action
        console.log("URL: ", url)
        const textarea = event.target.querySelector("textarea")
        const id = textarea.id
        console.log(textarea.value)
        console.log(id)
        const formData = new FormData();
        formData.set("translation", textarea.value);
        formData.set("lang", "en-UK");
        console.log("formdata: ", formData);
        fetch(url, {
            method: "POST",
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error("Network response was not ok");
                }
                return response;
            })
            .then(data => {
                // Handle the response data here (if needed)
                console.log("POST request successful:", data);
                Toastify({
                    text: `English Translation Saved:\n${id}`,
                    style: {
                        background: "green",
                    },
                    duration: 3000
                }).showToast();
            })
            .catch(error => {
                console.error("Error sending POST request:", error);
                Toastify({
                    text: "Failed to save English Translation!\nCopy the text, refresh this page, and try again.",
                    close: true,
                    style: {
                        background: "red",
                    },
                    duration: -1,
                }).showToast();
            });
    });
}
const textCommentForms = document.getElementsByName("textCommentForm");
textCommentForms.forEach(addTextCommentFormEventListener);
function addTextCommentFormEventListener(textCommentForm) {
    textCommentForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const url = event.target.action
        console.log("URL: ", url)
        const textarea = event.target.querySelector("textarea")
        const id = textarea.id
        console.log(textarea.value)
        console.log(id)
        const formData = new FormData();
        formData.set("textComment", textarea.value);
        console.log("formdata: ", formData);
        fetch(url, {
            method: "POST",
            body: formData
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error("Network response was not ok");
                }
                return response;
            })
            .then(data => {
                // Handle the response data here (if needed)
                console.log("POST request successful:", data);
                Toastify({
                    text: `Comment Saved:\n${id}`,
                    style: {
                        background: "green",
                    },
                    duration: 3000
                }).showToast();
            })
            .catch(error => {
                console.error("Error sending POST request:", error);
                Toastify({
                    text: "Failed to save comment!\nRefresh this page and try again.",
                    close: true,
                    style: {
                        background: "red",
                    },
                    duration: -1,
                }).showToast();
            });
    });
}

const elements = document.getElementsByName("includeSelect");
elements.forEach(addCheckboxEventListener)
function addCheckboxEventListener(checkbox) {
    checkbox.addEventListener("change", (event) => {
        console.log(event);
        console.log(event.target.selectedOptions[0].value);
        sendPostRequest(event.target.id,event.target.selectedOptions[0].value)
    });
}


function sendPostRequest(id, include) {
    console.log("sending POST request", id, include);
    const formData = new FormData();
    formData.set("include", include);
    console.log("formdata: ", formData);
    fetch("/query/"+id+"/include", {
        method: "POST",
        body: formData
    })
        .then(response => {
            if (!response.ok) {
                throw new Error("Network response was not ok");
            }
            return response;
        })
        .then(data => {
            // Handle the response data here (if needed)
            console.log("POST request successful:", data);
            Toastify({
                text: `Saved as ${include}:\n${id}`,
                style: {
                    background: "green",
                },
                duration: 3000
            }).showToast();
        })
        .catch(error => {
            console.error("Error sending POST request:", error);
            Toastify({
                text: "Failed to save changes!\nRefresh this page and try again.",
                close: true,
                style: {
                    background: "red",
                },
                duration: -1,
            }).showToast();
        });
}

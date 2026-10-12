import os
import unittest

from validation import RequestError, validate_filename, validate_request, validate_url

FOLDERS = {"checkpoints": [os.path.join("models", "checkpoints"), "extra"], "vae": [os.path.join("models", "vae")]}
HF = "https://huggingface.co/Comfy-Org/x/resolve/main/a.safetensors?download=true"


def request(**overrides):
    body = {"url": HF, "directory": "checkpoints", "filename": "a.safetensors"}
    body.update(overrides)
    return body


def status_of(fn):
    try:
        fn()
    except RequestError as e:
        return e.status
    return None


class ValidationTest(unittest.TestCase):

    def test_valid_request_goes_to_first_folder_path(self):
        url, directory, filename, target = validate_request(request(), FOLDERS, lambda p: False, False)
        self.assertEqual(HF, url)
        self.assertEqual("checkpoints", directory)
        self.assertEqual(os.path.join("models", "checkpoints", "a.safetensors"), target)

    def test_github_allowed(self):
        self.assertEqual("https://github.com/o/r/releases/download/v1/m.safetensors",
                         validate_url("https://github.com/o/r/releases/download/v1/m.safetensors"))

    def test_civitai_allowed(self):
        self.assertEqual("https://civitai.com/api/download/models/123",
                         validate_url("https://civitai.com/api/download/models/123"))
        self.assertEqual(403, status_of(lambda: validate_url("https://civitai.com.evil.com/api/download/models/1")))

    def test_http_rejected(self):
        self.assertEqual(400, status_of(lambda: validate_url("http://huggingface.co/a.safetensors")))

    def test_other_hosts_rejected(self):
        for url in ("https://example.com/a.safetensors",
                    "https://huggingface.co.evil.com/a.safetensors",
                    "https://evil.com/huggingface.co/a.safetensors"):
            self.assertEqual(403, status_of(lambda: validate_url(url)), url)

    def test_path_traversal_and_subfolders_rejected(self):
        for name in ("../a.safetensors", "sub/a.safetensors", "sub\\a.safetensors", "C:a.safetensors", ".hidden.safetensors", ""):
            self.assertEqual(400, status_of(lambda: validate_filename(name, False)), name)

    def test_pickle_formats_need_opt_in(self):
        self.assertEqual(403, status_of(lambda: validate_filename("a.ckpt", False)))
        self.assertEqual("a.ckpt", validate_filename("a.ckpt", True))
        self.assertEqual(403, status_of(lambda: validate_filename("a.exe", True)))
        self.assertEqual("a.GGUF", validate_filename("a.GGUF", False))

    def test_unknown_or_blocked_folder_rejected(self):
        self.assertEqual(400, status_of(lambda: validate_request(request(directory="loras"), FOLDERS, lambda p: False, False)))
        self.assertEqual(400, status_of(lambda: validate_request(request(directory="custom_nodes"),
                                                                  {"custom_nodes": ["cn"]}, lambda p: False, False)))

    def test_existing_file_conflicts(self):
        self.assertEqual(409, status_of(lambda: validate_request(request(), FOLDERS, lambda p: True, False)))

    def test_body_must_be_object(self):
        self.assertEqual(400, status_of(lambda: validate_request([], FOLDERS, lambda p: False, False)))


if __name__ == "__main__":
    unittest.main()

import os
import unittest
from unittest import mock

from downloader import CIVITAI_LOGIN_MESSAGE, GATED_MESSAGE, _auth_headers, _denied_message


class TokenTest(unittest.TestCase):

    @mock.patch.dict(os.environ, {"HF_TOKEN": "hf", "CIVITAI_TOKEN": "cv"})
    def test_each_token_goes_to_its_own_site_only(self):
        self.assertEqual({"Authorization": "Bearer hf"}, _auth_headers("https://huggingface.co/o/r/resolve/main/a.safetensors"))
        self.assertEqual({"Authorization": "Bearer cv"}, _auth_headers("https://civitai.com/api/download/models/1"))
        self.assertEqual({}, _auth_headers("https://github.com/o/r/raw/main/a.safetensors"))
        self.assertEqual({}, _auth_headers("https://civitai.com.evil.com/api/download/models/1"))

    @mock.patch.dict(os.environ, {}, clear=True)
    def test_no_token_no_header(self):
        self.assertEqual({}, _auth_headers("https://civitai.com/api/download/models/1"))

    def test_denied_message_names_the_right_token(self):
        self.assertEqual(CIVITAI_LOGIN_MESSAGE, _denied_message("https://civitai.com/api/download/models/1"))
        self.assertEqual(GATED_MESSAGE, _denied_message("https://huggingface.co/o/r/resolve/main/a.safetensors"))


if __name__ == "__main__":
    unittest.main()

"""Regression coverage for the documentation compilation guard."""
import runpy
import unittest
from pathlib import Path

unchecked = runpy.run_path(str(Path(__file__).with_name("check-doc-examples.py")))["unchecked_scala_lines"]


class FenceTests(unittest.TestCase):
    def test_scala_fence_variants(self):
        for fence in ("```", "````", "~~~", "~~~~"):
            for indent in ("", " ", "   "):
                for space in ("", " ", "\t"):
                    text = f"{indent}{fence}{space}scala\nval x = 1\n{indent}{fence}"
                    with self.subTest(text=text):
                        self.assertEqual(list(unchecked(text, readme=True)), [1])
                        self.assertEqual(list(unchecked(text)), [1])
                        self.assertEqual(list(unchecked(text.replace("scala", "scala mdoc"))), [])

    def test_only_opening_fences_are_checked(self):
        self.assertEqual(list(unchecked("````text\n```scala\n```\n````\n~~~scala\n~~~")), [5])
        self.assertEqual(list(unchecked("```scala mdoc\n~~~scala\n```")), [])

    def test_mdoc_is_a_separate_token(self):
        self.assertEqual(list(unchecked("~~~scala not-mdoc\n~~~")), [1])
        self.assertEqual(list(unchecked("~~~scala mdoc:silent\n~~~")), [])
        self.assertEqual(list(unchecked("~~~scala mdoc\n~~~", readme=True)), [1])


if __name__ == "__main__":
    unittest.main()

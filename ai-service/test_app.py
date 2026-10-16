import unittest
from app import CaseInput, predict, health


class ServiceTest(unittest.TestCase):
    def case(self, text, amount=80):
        return CaseInput(
            type="EXPENSE", title="Expense request", description=text, amount=amount
        )

    def test_response_contract(self):
        result = predict(
            self.case("Business travel for an approved client meeting with receipt")
        )
        self.assertIn(result["recommendation"], ["APPROVE", "REJECT", "REVIEW"])
        self.assertTrue(0 <= result["confidence"] <= 1)
        self.assertEqual(result["modelVersion"], health()["model"])

    def test_same_amount_different_text_changes_recommendation(self):
        business = predict(
            self.case(
                "Approved business travel to a client meeting with receipt attached"
            )
        )
        private = predict(
            self.case(
                "Personal gaming subscription for private entertainment unrelated to work"
            )
        )
        self.assertEqual(business["recommendation"], "APPROVE")
        self.assertEqual(private["recommendation"], "REJECT")

    def test_amount_is_a_separate_policy_check(self):
        text = "Approved business travel to a client meeting with receipt attached"
        self.assertEqual(predict(self.case(text, 80)), predict(self.case(text, 1200)))

    def test_unknown_vocabulary_abstains(self):
        result = predict(
            CaseInput(
                type="EXPENSE",
                title="zzqqzz",
                description="zzqqzz yyxxzz qqqwww rrrttt",
                amount=80,
            )
        )
        self.assertEqual(result["recommendation"], "REVIEW")
        self.assertEqual(result["confidence"], 0)

    def test_invalid_input_is_rejected(self):
        from pydantic import ValidationError

        with self.assertRaises(ValidationError):
            CaseInput(type="UNKNOWN", title="", description="short")


if __name__ == "__main__":
    unittest.main()

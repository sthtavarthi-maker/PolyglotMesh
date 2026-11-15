import { CtaSection } from '../components/landing/CtaSection'
import { FeaturesSection } from '../components/landing/FeaturesSection'
import { HeroSection } from '../components/landing/HeroSection'
import { HowItWorksSection } from '../components/landing/HowItWorksSection'
import { LanguagesSection } from '../components/landing/LanguagesSection'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/**
 * Landing page. Each section is a self-contained component so the page stays
 * readable and sections can be reordered or reused independently.
 */
export function HomePage() {
  useDocumentTitle()

  return (
    <>
      <HeroSection />
      <LanguagesSection />
      <FeaturesSection />
      <HowItWorksSection />
      <CtaSection />
    </>
  )
}
